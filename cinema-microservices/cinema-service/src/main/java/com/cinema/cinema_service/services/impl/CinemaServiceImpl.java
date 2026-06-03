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
import com.cinema.cinema_service.enums.CinemaStatus;
import com.cinema.cinema_service.grpc.BookingGrpcClient;
import com.cinema.cinema_service.grpc.HallGrpcClient;
import com.cinema.cinema_service.grpc.ShowtimeGrpcClient;
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
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    HallGrpcClient hallGrpcClient;
    ShowtimeGrpcClient showtimeGrpcClient;
    BookingGrpcClient bookingGrpcClient;

    // CRUD rap phim
    @Override
    @Transactional
    public ActionMessageResponse createCinema(CreateCinemaRequest request, HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        validateCoordinate(request.getLatitude(), request.getLongitude());
        validateOperatingTime(request.getOpenTime(), request.getCloseTime());
        validateCinemaCodeNotExists(request.getCode(), null);

        Cinema cinema = cinemaMapper.toEntity(request);
        cinema.setCode(normalizeCode(request.getCode()));
        cinemaRepository.save(cinema);

        return ActionMessageResponse.builder()
                .message("Tạo rạp phim thành công")
                .build();
    }

    // Lay chi tiet rap, sau do gan them staff va ten manager cho response.
    @Override
    @Transactional(readOnly = true)
    public CinemaResponse getCinemaById(UUID cinemaId) {
        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        authorizeCinemaReadAccess(cinema);
        List<UUID> staffIds = getActiveStaffIdsByCinemaId(cinemaId);
        CinemaResponse response = cinemaMapper.toResponse(cinema, staffIds);
        populateManagerName(response);
        return response;
    }

    // Tim rap theo keyword/filter/page, dong thoi scope lai theo role dang nhap.
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CinemaResponse> searchCinemas(PageRequest<CinemaField> request) {
        PageRequest<CinemaField> scopedRequest = scopeCinemaSearchRequest(request);
        if (scopedRequest == null) {
            return emptyCinemaPageResponse(request);
        }
        return searchCinemaPageInternal(scopedRequest);
    }

    // Cap nhat thong tin rap; flow nay chua cho doi ma rap.
    @Override
    @Transactional
    public ActionMessageResponse updateCinema(UUID cinemaId, UpdateCinemaRequest request,
            HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        validateCoordinate(request.getLatitude(), request.getLongitude());
        validateOperatingTime(request.getOpenTime(), request.getCloseTime());

        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        validateNoActiveBookingForCinema(cinemaId);
        cinemaMapper.updateEntity(cinema, request);
        cinemaRepository.save(cinema);

        return ActionMessageResponse.builder()
                .message("Cập nhật rạp thành công")
                .build();
    }

    // Chi cap nhat trang thai rap.
    @Override
    @Transactional
    public ActionMessageResponse updateCinemaStatus(
            UUID cinemaId,
            UpdateCinemaStatusRequest request,
            HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        validateNoActiveBookingForCinema(cinemaId);
        cinema.setStatus(request.getStatus());
        cinemaRepository.save(cinema);

        return ActionMessageResponse.builder()
                .message("Cập nhật trạng thái rạp thành công")
                .build();
    }

    // Xoa mem rap va vo hieu hoa toan bo staff dang gan.
    @Override
    @Transactional
    public ActionMessageResponse deleteCinema(UUID cinemaId, HttpServletRequest httpRequest) {
        validateAdminRole(httpRequest);
        Cinema cinema = getActiveCinemaOrThrow(cinemaId);
        validateNoActiveBookingForCinema(cinemaId);
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

    // Gan staff vao rap, chi admin duoc thao tac.
    @Override
    @Transactional
    public ActionMessageResponse assignStaff(UUID cinemaId, AssignCinemaStaffRequest request,
            HttpServletRequest httpRequest) {
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

    // Cap nhat lien ket staff-rap: neu staff da co record thi mo ra va gan lai.
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
                .message("Cập nhật nhân viên vào rạp thành công")
                .build();
    }

    // Go staff khoi rap bang cach tat active thay vi xoa du lieu.
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

    // Doc danh sach staff cua rap, chi cho role co quyen xem.
    @Override
    @Transactional(readOnly = true)
    public List<CinemaStaffResponse> getCinemaStaffs(UUID cinemaId, HttpServletRequest httpRequest) {
        validateReadRole(httpRequest);
        authorizeCinemaReadAccess(getActiveCinemaOrThrow(cinemaId));
        return cinemaStaffRepository.findByCinemaId(cinemaId).stream()
                .map(cinemaMapper::toResponse)
                .toList();
    }

    // Rap cua toi: manager/staff lay danh sach rap co quyen truy cap.
    @Override
    @Transactional(readOnly = true)
    public List<CinemaResponse> getMyManagedCinemas(HttpServletRequest httpRequest) {
        validateSelfReadRole(httpRequest);
        UUID managerId = extractUserId(httpRequest);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        return getAccessibleCinemasByUserId(managerId, role).stream()
                .filter(cinema -> cinema.getStatus() == CinemaStatus.ACTIVE)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CinemaResponse> searchMyManagedCinemas(PageRequest<CinemaField> request,
                                                                HttpServletRequest httpRequest) {
        validateSelfReadRole(httpRequest);
        PageRequest<CinemaField> scopedRequest = scopeMyCinemaSearchRequest(request, httpRequest);
        if (scopedRequest == null) {
            return emptyCinemaPageResponse(request);
        }
        return searchCinemaPageInternal(scopedRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CinemaResponse> getAllActiveCinemas() {
        return mapManagedCinemasToResponses(cinemaRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc());
    }

    // Manager xem cac rap do minh quan ly.
    @Override
    @Transactional(readOnly = true)
    public List<CinemaResponse> getCinemasByManagerId(UUID managerId) {
        return mapManagedCinemasToResponses(
                cinemaRepository.findAllByManagerIdAndIsDeletedFalseOrderByCreatedAtDesc(managerId));
    }

    // Staff xem rap dang gan, con manager xem rap do minh quan ly.
    @Override
    @Transactional(readOnly = true)
    public List<CinemaResponse> getAccessibleCinemasByUserId(UUID userId, String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (HeaderNames.ROLE_STAFF.equals(normalizedRole)) {
            return getCinemasByStaffId(userId);
        }
        return getCinemasByManagerId(userId);
    }

    // Staff chi lay rap co lien ket active, sau do map thanh response day du.
    private List<CinemaResponse> getCinemasByStaffId(UUID staffId) {
        return cinemaStaffRepository.findByStaffId(staffId)
                .filter(CinemaStaff::getActive)
                .flatMap(staffLink -> cinemaRepository.findByIdAndIsDeletedFalse(staffLink.getCinemaId()))
                .filter(this::isActiveCinema)
                .map(this::mapCinemaToResponse)
                .stream()
                .toList();
    }

    // Tim rap dang hoat dong, khong co thi tra loi business error.
    private Cinema getActiveCinemaOrThrow(UUID cinemaId) {
        return cinemaRepository.findByIdAndIsDeletedFalse(cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CINEMA_NOT_FOUND));
    }

    // Gom staff theo rap de response list khong bi query lap lai nhieu.
    private List<CinemaResponse> mapManagedCinemasToResponses(List<Cinema> cinemas) {
        Map<UUID, List<UUID>> staffByCinema = mapActiveStaffIdsByCinema(cinemas);
        List<CinemaResponse> responses = cinemas.stream()
                .map(cinema -> cinemaMapper.toResponse(cinema,
                        staffByCinema.getOrDefault(cinema.getId(), List.of())))
                .toList();
        populateManagerNames(responses);
        return responses;
    }

    // Map 1 rap don le va bo sung ten manager neu co.
    private CinemaResponse mapCinemaToResponse(Cinema cinema) {
        List<UUID> staffIds = getActiveStaffIdsByCinemaId(cinema.getId());
        CinemaResponse response = cinemaMapper.toResponse(cinema, staffIds);
        populateManagerName(response);
        return response;
    }

    // Chuoi search page chung cho search rap.
    private PageResponse<CinemaResponse> searchCinemaPageInternal(PageRequest<CinemaField> scopedRequest) {
        String keyword = scopedRequest.getNormalizedKeyword();
        int page = scopedRequest.getPageOrDefault();
        int size = scopedRequest.getSizeOrDefault();

        List<SortField<CinemaField>> sortFields = scopedRequest.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        boolean hasIdSort = sortFields.stream()
                .anyMatch(sort -> sort != null && sort.getField() == CinemaField.ID);
        if (!hasIdSort) {
            sortFields.add(new SortField<>(CinemaField.ID, "ASC"));
        }

        List<FilterField<CinemaField>> filterFields = scopedRequest.getFilterBy();
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

    // Chi lay staff active cua rap.
    private List<UUID> getActiveStaffIdsByCinemaId(UUID cinemaId) {
        return cinemaStaffRepository.findByCinemaIdAndActiveTrue(cinemaId).stream()
                .map(CinemaStaff::getStaffId)
                .toList();
    }

    private boolean isActiveCinema(Cinema cinema) {
        return cinema != null && cinema.getStatus() == CinemaStatus.ACTIVE;
    }

    // Build map cinemaId -> staffIds de render list rap nhanh hon.
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

    // Enrich ten manager theo managerId, co cache nho trong cung request.
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

    // Enrich ten manager cho 1 response don le.
    private void populateManagerName(CinemaResponse response) {
        if (response == null || response.getManagerId() == null) {
            return;
        }
        response.setManagerName(resolveManagerNameSafely(response.getManagerId()));
    }

    // Lay ten manager tu user-service; neu loi thi fallback null de khong fail
    // luong chinh.
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

    // Gio mo phai truoc gio dong.
    private void validateOperatingTime(java.time.LocalTime openTime, java.time.LocalTime closeTime) {
        if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // Kiem tra toa do hop le theo khoang do dia ly co ban.
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

    private void validateNoActiveBookingForCinema(UUID cinemaId) {
        List<UUID> hallIds = hallGrpcClient.listActiveHallIdsByCinema(cinemaId);
        if (hallIds.isEmpty()) {
            return;
        }

        List<UUID> activeShowtimeIds = new ArrayList<>();
        for (UUID hallId : hallIds) {
            activeShowtimeIds.addAll(showtimeGrpcClient.listActiveShowtimeIdsByHall(hallId));
        }
        if (activeShowtimeIds.isEmpty()) {
            return;
        }

        if (bookingGrpcClient.hasActiveBookingByShowtimeIds(activeShowtimeIds)) {
            throw new BusinessException(ErrorCode.NOT_UPDATE_BOOKED_SHOWTIME);
        }
    }

    // Dung chung cho create/update: null = tao moi, khac null = update va loai tru
    // cinema hien tai.
    private void validateCinemaCodeNotExists(String code, UUID cinemaId) {
        String normalizedCode = normalizeCode(code);
        boolean existed;
        // Kiểm tra trùng mã rạp -- tạo mới
        if (cinemaId == null) {
            existed = cinemaRepository.existsByCodeIgnoreCaseAndIsDeletedFalse(normalizedCode);
        }
        // Kiểm tra trùng mã rạp -- cập nhật
        else {
            existed = cinemaRepository.existsByCodeIgnoreCaseAndIdNotAndIsDeletedFalse(normalizedCode, cinemaId);
        }
        if (existed) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }
    }

    // Doc userId tu header, neu sai format thi log de debug request auth.
    private UUID extractUserId(HttpServletRequest httpRequest) {
        try {
            return RequestAuthUtils.requireUserId(httpRequest);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_FORMAT) {
                log.warn("Invalid auth headers method={} path={}", httpRequest.getMethod(),
                        httpRequest.getRequestURI());
            }
            throw ex;
        }
    }

    // Chuan hoa code truoc khi validate/save.
    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return code.trim().toUpperCase();
    }

    // Chi admin duoc chay cac thao tac quan tri rap.
    private void validateAdminRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, "cinema_admin_action");
    }

    // Helper cho cac endpoint chi manager trong tuong lai.
    private void validateManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER, log, "cinema_manager_action");
    }

    // Cho phep doc cho admin/manager/staff.
    private void validateReadRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
    }

    // Chi manager/staff xem duoc du lieu 'cua toi'.
    private void validateSelfReadRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
    }

    // Kiem tra quyen doc 1 rap cu the theo admin/manager/staff.
    private void authorizeCinemaReadAccess(Cinema cinema) {
        HttpServletRequest currentRequest = getCurrentHttpRequest();
        if (currentRequest == null) {
            return;
        }

        RequestAuthUtils.requireAnyRole(currentRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
        String role = RequestAuthUtils.requireRoleHeader(currentRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return;
        }

        UUID userId = RequestAuthUtils.requireUserId(currentRequest);
        if (HeaderNames.ROLE_MANAGER.equals(role)) {
            if (!userId.equals(cinema.getManagerId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        if (HeaderNames.ROLE_STAFF.equals(role)) {
            CinemaStaff assignedCinema = cinemaStaffRepository.findByStaffId(userId)
                    .filter(CinemaStaff::getActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
            if (!cinema.getId().equals(assignedCinema.getCinemaId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    // Tu dong gioi han ket qua search theo role dang nhap.
    private PageRequest<CinemaField> scopeCinemaSearchRequest(PageRequest<CinemaField> request) {
        HttpServletRequest currentRequest = getCurrentHttpRequest();
        if (currentRequest == null) {
            return request;
        }

        RequestAuthUtils.requireAnyRole(currentRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
        String role = RequestAuthUtils.requireRoleHeader(currentRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return request;
        }

        UUID userId = RequestAuthUtils.requireUserId(currentRequest);
        List<FilterField<CinemaField>> filterFields = request.getFilterBy() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getFilterBy());

        if (HeaderNames.ROLE_MANAGER.equals(role)) {
            filterFields.add(FilterField.<CinemaField>builder()
                    .field(CinemaField.MANAGER_ID)
                    .operator("EQ")
                    .value(userId)
                    .build());
        } else if (HeaderNames.ROLE_STAFF.equals(role)) {
            CinemaStaff staffLink = cinemaStaffRepository.findByStaffId(userId)
                    .filter(CinemaStaff::getActive)
                    .orElse(null);
            if (staffLink == null) {
                return null;
            }
            filterFields.add(FilterField.<CinemaField>builder()
                    .field(CinemaField.ID)
                    .operator("EQ")
                    .value(staffLink.getCinemaId())
                    .build());
        }

        return PageRequest.<CinemaField>builder()
                .page(request.getPage())
                .size(request.getSize())
                .keyword(request.getKeyword())
                .sortBy(request.getSortBy() == null ? null : new ArrayList<>(request.getSortBy()))
                .filterBy(filterFields)
                .build();
    }

    private PageRequest<CinemaField> scopeMyCinemaSearchRequest(PageRequest<CinemaField> request,
                                                                HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);

        List<FilterField<CinemaField>> filterFields = request.getFilterBy() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getFilterBy());
        filterFields.add(FilterField.<CinemaField>builder()
                .field(CinemaField.STATUS)
                .operator("EQ")
                .value(CinemaStatus.ACTIVE)
                .build());

        if (HeaderNames.ROLE_MANAGER.equals(role)) {
            filterFields.add(FilterField.<CinemaField>builder()
                    .field(CinemaField.MANAGER_ID)
                    .operator("EQ")
                    .value(userId)
                    .build());
        } else if (HeaderNames.ROLE_STAFF.equals(role)) {
            CinemaStaff staffLink = cinemaStaffRepository.findByStaffId(userId)
                    .filter(CinemaStaff::getActive)
                    .orElse(null);
            if (staffLink == null) {
                return null;
            }
            filterFields.add(FilterField.<CinemaField>builder()
                    .field(CinemaField.ID)
                    .operator("EQ")
                    .value(staffLink.getCinemaId())
                    .build());
        }

        return PageRequest.<CinemaField>builder()
                .page(request.getPage())
                .size(request.getSize())
                .keyword(request.getKeyword())
                .sortBy(request.getSortBy() == null ? null : new ArrayList<>(request.getSortBy()))
                .filterBy(filterFields)
                .build();
    }

    // Tra ve page rong khi user khong co rap nao duoc scope.
    private PageResponse<CinemaResponse> emptyCinemaPageResponse(PageRequest<CinemaField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        return PageResponse.<CinemaResponse>builder()
                .data(List.of())
                .currentPage(page)
                .totalPages(0)
                .totalElements(0L)
                .size(size)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }

    // Lay request hien tai tu RequestContext de phuc vu authorize trong luong read.
    private HttpServletRequest getCurrentHttpRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
