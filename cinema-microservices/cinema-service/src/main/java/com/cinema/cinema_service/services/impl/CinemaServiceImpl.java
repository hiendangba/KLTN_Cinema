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
import com.cinema.cinema_service.mapper.CinemaMapper;
import com.cinema.cinema_service.repository.CinemaRepository;
import com.cinema.cinema_service.repository.CinemaRepositoryImpl;
import com.cinema.cinema_service.repository.CinemaStaffRepository;
import com.cinema.cinema_service.services.CinemaService;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
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
                .message("Tạo rạp thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CinemaResponse getCinemaById(UUID cinemaId) {
        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        List<UUID> staffIds = getActiveStaffIdsByCinemaId(cinemaId);
        return cinemaMapper.toResponse(cinema, staffIds);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<CinemaResponse> searchCinemas(CursorPageRequest<CinemaField> request) {
        String[] cursorParts = request.getParsedCompositeCursor();
        String keyword = request.getNormalizedKeyword();
        int size = request.getSizeOrDefault();

        List<SortField<CinemaField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        sortFields.add(new SortField<>(CinemaField.ID, "ASC"));

        List<FilterField<CinemaField>> filterFields = request.getFilterBy();
        List<Cinema> cinemas = cinemaRepositoryImpl.searchWithCursorAndSortAndFilter(
                cursorParts, keyword, size, sortFields, filterFields);

        boolean hasNext = cinemas.size() > size;
        String nextCursor = null;
        if (hasNext) {
            cinemas = cinemas.subList(0, size);
            nextCursor = CursorPageRequest.encodeCompositeCursor(
                    CinemaField.getFieldValues(cinemas.get(cinemas.size() - 1), sortFields));
        }

        String prevCursor = null;
        if (cursorParts != null && cursorParts.length > 0) {
            List<Cinema> prevCinemas = cinemaRepositoryImpl.previousCursor(cursorParts, keyword, size, sortFields, filterFields);
            if (!prevCinemas.isEmpty() && prevCinemas.size() == size) {
                prevCursor = CursorPageRequest.encodeCompositeCursor(
                        CinemaField.getFieldValues(prevCinemas.get(size - 1), sortFields));
            }
        }

        Map<UUID, List<UUID>> staffByCinema = mapActiveStaffIdsByCinema(cinemas);
        List<CinemaResponse> data = cinemas.stream()
                .map(cinema -> cinemaMapper.toResponse(cinema, staffByCinema.getOrDefault(cinema.getId(), List.of())))
                .toList();

        return CursorPageResponse.<CinemaResponse>builder()
                .data(data)
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasNext(hasNext)
                .size(data.size())
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
        return cinemaMapper.toResponse(cinema, staffIds);
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
        String userIdRaw = httpRequest.getHeader(HeaderNames.X_USER_ID);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return code.trim().toUpperCase();
    }

    private void validateAdminRole(HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!HeaderNames.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!HeaderNames.ROLE_MANAGER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
