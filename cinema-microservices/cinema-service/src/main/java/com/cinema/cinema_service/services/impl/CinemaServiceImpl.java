package com.cinema.cinema_service.services.impl;

import com.cinema.cinema_service.dto.request.AssignCinemaStaffRequest;
import com.cinema.cinema_service.dto.request.CinemaField;
import com.cinema.cinema_service.dto.request.CreateCinemaRequest;
import com.cinema.cinema_service.dto.request.UpdateCinemaRequest;
import com.cinema.cinema_service.dto.request.UpdateCinemaStatusRequest;
import com.cinema.cinema_service.dto.response.CinemaResponse;
import com.cinema.cinema_service.dto.response.CinemaStaffResponse;
import com.cinema.cinema_service.entity.Cinema;
import com.cinema.cinema_service.entity.CinemaStaff;
import com.cinema.cinema_service.grpc.UserGrpcClient;
import com.cinema.cinema_service.mapper.CinemaMapper;
import com.cinema.cinema_service.repository.CinemaRepository;
import com.cinema.cinema_service.repository.CinemaRepositoryImpl;
import com.cinema.cinema_service.repository.CinemaStaffRepository;
import com.cinema.cinema_service.services.CinemaService;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CinemaServiceImpl implements CinemaService {

    CinemaRepository cinemaRepository;
    CinemaRepositoryImpl cinemaRepositoryImpl;
    CinemaStaffRepository cinemaStaffRepository;
    CinemaMapper cinemaMapper;
    UserGrpcClient userGrpcClient;

    @Override
    @Transactional
    public ActionMessageResponse createCinema(CreateCinemaRequest request, HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        validateCoordinate(request.getLatitude(), request.getLongitude());
        validateOperatingTime(request.getOpenTime(), request.getCloseTime());
        validateCinemaCodeNotExists(request.getCode(), null);
        validateManagerNotAssigned(request.getManagerId(), null);

        Cinema cinema = cinemaMapper.toEntity(request);
        cinema.setCode(normalizeCode(request.getCode()));
        cinemaRepository.save(cinema);

        return ActionMessageResponse.builder()
                .message("Tạo rạp phim thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaResponse getCinemaById(UUID cinemaId) {
        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        List<UUID> staffIds = getActiveStaffIdsByCinemaId(cinemaId);
        CinemaResponse response = cinemaMapper.toResponse(cinema, staffIds);
        populateManagerName(response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CinemaResponse> searchCinemas(PageRequest<CinemaField> request) {
        String keyword = request.getNormalizedKeyword();
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();

        List<SortField<CinemaField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        boolean hasIdSort = sortFields.stream()
                .anyMatch(sort -> sort != null && sort.getField() == CinemaField.ID);
        if (!hasIdSort) {
            sortFields.add(new SortField<>(CinemaField.ID, "ASC"));
        }

        List<FilterField<CinemaField>> filterFields = request.getFilterBy();
        long totalElements = cinemaRepositoryImpl.countWithFilter(keyword, filterFields);
        List<Cinema> cinemas = cinemaRepositoryImpl.searchWithPageAndSortAndFilter(
                keyword, page, size, sortFields, filterFields);

        Map<UUID, List<UUID>> staffByCinema = mapActiveStaffIdsByCinema(cinemas);
        List<CinemaResponse> data = cinemas.stream()
                .map(cinema -> cinemaMapper.toResponse(cinema, staffByCinema.getOrDefault(cinema.getId(), List.of())))
                .collect(java.util.stream.Collectors.toList());
        populateManagerNames(data);

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return PageResponse.<CinemaResponse>builder()
                .data(data)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateCinema(UUID cinemaId, UpdateCinemaRequest request, HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        validateCoordinate(request.getLatitude(), request.getLongitude());
        validateOperatingTime(request.getOpenTime(), request.getCloseTime());
        validateManagerNotAssigned(request.getManagerId(), cinemaId);

        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        cinemaMapper.updateEntity(cinema, request);
        cinemaRepository.save(cinema);

        return ActionMessageResponse.builder()
                .message("Cập nhật rạp thành công")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateCinemaStatus(
            UUID cinemaId,
            UpdateCinemaStatusRequest request,
            HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        cinema.setStatus(request.getStatus());
        cinemaRepository.save(cinema);

        return ActionMessageResponse.builder()
                .message("Cập nhật trạng thái rạp thành công")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteCinema(UUID cinemaId, HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        cinema.setIsDeleted(true);
        cinemaRepository.save(cinema);

        List<CinemaStaff> cinemaStaffs = cinemaStaffRepository.findByCinemaIdAndActiveTrue(cinemaId);
        for (CinemaStaff cinemaStaff : cinemaStaffs) {
            cinemaStaff.setActive(false);
        }
        cinemaStaffRepository.saveAll(cinemaStaffs);

        return ActionMessageResponse.builder()
                .message("Xóa rạp thành công")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse assignStaff(UUID cinemaId, AssignCinemaStaffRequest request, HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        getActiveCinemaOrThrow(cinemaId);

        if (cinemaStaffRepository.existsByStaffId(request.getStaffId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        CinemaStaff cinemaStaff = new CinemaStaff();
        cinemaStaff.setCinemaId(cinemaId);
        cinemaStaff.setStaffId(request.getStaffId());
        cinemaStaff.setActive(true);
        cinemaStaffRepository.save(cinemaStaff);

        return ActionMessageResponse.builder()
                .message("Gán nhân viên vào rạp thành công")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateStaffAssignment(UUID cinemaId, AssignCinemaStaffRequest request,
                                                       HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        getActiveCinemaOrThrow(cinemaId);

        CinemaStaff cinemaStaff = cinemaStaffRepository.findByStaffId(request.getStaffId())
                .orElseGet(CinemaStaff::new);
        cinemaStaff.setCinemaId(cinemaId);
        cinemaStaff.setStaffId(request.getStaffId());
        cinemaStaff.setActive(true);
        cinemaStaffRepository.save(cinemaStaff);

        return ActionMessageResponse.builder()
                .message("Cap nhat nhan vien vao rap thanh cong")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse unassignStaff(UUID cinemaId, UUID staffId, HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        getActiveCinemaOrThrow(cinemaId);

        CinemaStaff cinemaStaff = cinemaStaffRepository.findByCinemaIdAndStaffId(cinemaId, staffId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!cinemaStaff.getActive()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        cinemaStaff.setActive(false);
        cinemaStaffRepository.save(cinemaStaff);

        return ActionMessageResponse.builder()
                .message("Gỡ nhân viên khỏi rạp thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CinemaStaffResponse> getCinemaStaffs(UUID cinemaId) {
        getActiveCinemaOrThrow(cinemaId);
        return cinemaStaffRepository.findByCinemaId(cinemaId).stream()
                .map(cinemaMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaResponse getMyManagedCinema(HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID managerId = extractUserId(httpRequest);
        return getCinemaByManagerId(managerId);
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaResponse getCinemaByManagerId(UUID managerId) {
        Cinema cinema = cinemaRepository.findByManagerIdAndIsDeletedFalse(managerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
        List<UUID> staffIds = getActiveStaffIdsByCinemaId(cinema.getId());
        CinemaResponse response = cinemaMapper.toResponse(cinema, staffIds);
        populateManagerName(response);
        return response;
    }

    private Cinema getActiveCinemaOrThrow(UUID cinemaId) {
        return cinemaRepository.findByIdAndIsDeletedFalse(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
    }

    private List<UUID> getActiveStaffIdsByCinemaId(UUID cinemaId) {
        return cinemaStaffRepository.findByCinemaIdAndActiveTrue(cinemaId).stream()
                .map(CinemaStaff::getStaffId)
                .toList();
    }

    private Map<UUID, List<UUID>> mapActiveStaffIdsByCinema(List<Cinema> cinemas) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        if (cinemas.isEmpty()) {
            return result;
        }

        List<UUID> cinemaIds = cinemas.stream()
                .map(Cinema::getId)
                .toList();
        List<CinemaStaff> staffLinks = cinemaStaffRepository.findByCinemaIdInAndActiveTrue(cinemaIds);
        for (CinemaStaff staffLink : staffLinks) {
            result.computeIfAbsent(staffLink.getCinemaId(), id -> new ArrayList<>())
                    .add(staffLink.getStaffId());
        }
        return result;
    }

    private void populateManagerNames(List<CinemaResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        Map<UUID, String> managerNameCache = new HashMap<>();
        for (CinemaResponse response : responses) {
            if (response == null || response.getManagerId() == null) {
                continue;
            }
            UUID managerId = response.getManagerId();
            String managerName = managerNameCache.computeIfAbsent(managerId, this::resolveManagerNameSafely);
            response.setManagerName(managerName);
        }
    }

    private void populateManagerName(CinemaResponse response) {
        if (response == null || response.getManagerId() == null) {
            return;
        }
        response.setManagerName(resolveManagerNameSafely(response.getManagerId()));
    }

    private String resolveManagerNameSafely(UUID managerId) {
        try {
            return userGrpcClient.getUserNameById(managerId);
        } catch (BusinessException ex) {
            log.warn("Cannot resolve manager name from user-service for managerId={}, errorKey={}",
                    managerId, ex.getErrorCode().name());
            return null;
        } catch (Exception ex) {
            log.warn("Unexpected error when resolving manager name for managerId={}", managerId, ex);
            return null;
        }
    }

    private void validateOperatingTime(java.time.LocalTime openTime, java.time.LocalTime closeTime) {
        if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateCoordinate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateCinemaCodeNotExists(String code, UUID cinemaId) {
        String normalizedCode = normalizeCode(code);
        boolean existed;
        if (cinemaId == null) {
            existed = cinemaRepository.existsByCodeIgnoreCaseAndIsDeletedFalse(normalizedCode);
        } else {
            existed = cinemaRepository.existsByCodeIgnoreCaseAndIdNotAndIsDeletedFalse(normalizedCode, cinemaId);
        }
        if (existed) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }
    }

    private void validateManagerNotAssigned(UUID managerId, UUID cinemaId) {
        if (managerId == null) {
            return;
        }
        boolean existed;
        if (cinemaId == null) {
            existed = cinemaRepository.existsByManagerIdAndIsDeletedFalse(managerId);
        } else {
            existed = cinemaRepository.existsByManagerIdAndIdNotAndIsDeletedFalse(managerId, cinemaId);
        }
        if (existed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private UUID extractUserId(HttpServletRequest httpRequest) {
        try {
            return RequestAuthUtils.requireUserId(httpRequest);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_FORMAT) {
                log.warn("Invalid auth headers method={} path={}", httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            throw ex;
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return code.trim().toUpperCase();
    }

    private void validateAdminRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, "cinema_admin_action");
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER, log, "cinema_manager_action");
    }
}
