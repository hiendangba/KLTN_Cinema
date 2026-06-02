package com.cinema.showtime_service.services.impl;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.dto.response.SuccessResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.showtime_service.dto.request.SearchShowtimesByFilmRequest;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
import com.cinema.showtime_service.dto.response.CinemaOperatingHoursResponse;
import com.cinema.showtime_service.dto.response.HallResponse;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import com.cinema.showtime_service.dto.response.SeatMapCellResponse;
import com.cinema.showtime_service.dto.response.SeatMapResponse;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.grpc.BookingGrpcClient;
import com.cinema.showtime_service.grpc.CinemaGrpcClient;
import com.cinema.showtime_service.grpc.FilmGrpcClient;
import com.cinema.showtime_service.grpc.HallGrpcClient;
import com.cinema.showtime_service.grpc.SeatGrpcClient;
import com.cinema.showtime_service.mapper.PricingPolicyMapper;
import com.cinema.showtime_service.mapper.ShowTimeMapper;
import com.cinema.showtime_service.repository.PricingPolicyRepository;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import com.cinema.showtime_service.repository.ShowTimeRepositoryImpl;
import com.cinema.showtime_service.services.ShowTimeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShowTimeServiceImpl implements ShowTimeService {

    ShowTimeRepository showTimeRepository;
    ShowTimeRepositoryImpl showTimeRepositoryImpl;
    ShowTimeMapper showTimeMapper;
    PricingPolicyMapper pricingPolicyMapper;
    FilmGrpcClient filmGrpcClient;
    BookingGrpcClient bookingGrpcClient;
    CinemaGrpcClient cinemaGrpcClient;
    HallGrpcClient hallGrpcClient;
    SeatGrpcClient seatGrpcClient;
    PricingPolicyRepository pricingPolicyRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowTimeResponse> searchShowtimes(PageRequest<ShowTimeField> request,
                                                          HttpServletRequest httpRequest) {
        PageRequest<ShowTimeField> scopedRequest = scopeShowtimeSearchRequest(request, httpRequest);
        if (scopedRequest == null) {
            return emptyShowtimePageResponse(request.getPage(), request.getSize());
        }
        return enrichShowtimePage(searchShowtimesBase(scopedRequest));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowTimeResponse> searchShowtimesByFilmId(UUID filmId, SearchShowtimesByFilmRequest request) {
        if (request == null || request.getDate() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<FilterField<ShowTimeField>> filters = new ArrayList<>();
        filters.add(FilterField.<ShowTimeField>builder()
                .field(ShowTimeField.FILM_ID)
                .operator("EQ")
                .value(filmId)
                .build());

        LocalDateTime startOfDay = request.getDate().atStartOfDay();
        LocalDateTime endOfDay = request.getDate().plusDays(1).atStartOfDay().minusNanos(1);
        filters.add(FilterField.<ShowTimeField>builder()
                .field(ShowTimeField.START_DATE_TIME)
                .operator("BETWEEN")
                .value(List.of(startOfDay, endOfDay))
                .build());
        filters.add(FilterField.<ShowTimeField>builder()
                .field(ShowTimeField.START_DATE_TIME)
                .operator("GTE")
                .value(LocalDateTime.now())
                .build());
        filters.addAll(buildActiveShowtimeStatusFilters());

        if (request.getCinemaId() != null) {
            List<UUID> hallIds = hallGrpcClient.listActiveHallIdsByCinema(request.getCinemaId());
            if (hallIds == null || hallIds.isEmpty()) {
                return emptyShowtimePageResponse(request.getPage(), request.getSize());
            }
            filters.add(FilterField.<ShowTimeField>builder()
                    .field(ShowTimeField.HALL_ID)
                    .operator("IN")
                    .value(hallIds)
                    .build());
        }

        PageRequest<ShowTimeField> scopedRequest = PageRequest.<ShowTimeField>builder()
                .page(request.getPage())
                .size(request.getSize())
                .sortBy(List.of(
                        new SortField<>(ShowTimeField.START_DATE_TIME, "ASC"),
                        new SortField<>(ShowTimeField.ID, "ASC")))
                .filterBy(filters)
                .build();
        return enrichShowtimePage(searchShowtimesBase(scopedRequest));
    }

    @Override
    @Transactional
    public int promoteScheduledShowtimesToOngoing(LocalDateTime now, int windowDays) {
        LocalDateTime referenceNow = now == null ? LocalDateTime.now() : now;
        int effectiveWindowDays = Math.max(1, windowDays);
        LocalDateTime windowStart = referenceNow.minusDays(effectiveWindowDays);
        int updated = showTimeRepository.promoteScheduledToOngoing(windowStart, referenceNow);

        log.info(
                "SHOWTIME_STATUS_TRANSITION scheduled->ongoing updated={} now={} windowDays={} windowStart={}",
                updated,
                referenceNow,
                effectiveWindowDays,
                windowStart);
        return updated;
    }

    @Override
    @Transactional
    public int expireOngoingShowtimes(LocalDateTime now) {
        LocalDateTime referenceNow = now == null ? LocalDateTime.now() : now;
        int updated = showTimeRepository.expireOngoingShowtimes(referenceNow);

        log.info(
                "SHOWTIME_STATUS_TRANSITION ongoing->finished updated={} now={}",
                updated,
                referenceNow);
        return updated;
    }

    private PageResponse<ShowTimeResponse> searchShowtimesBase(PageRequest<ShowTimeField> request) {
        log.info("Lay danh sach showtime (page={}, size={}, keyword={}, sortBy={}, filterBy={})",
                request.getPage(), request.getSize(), request.getKeyword(), request.getSortBy(),
                request.getFilterBy());

        String keyword = request.getNormalizedKeyword();
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();

        List<SortField<ShowTimeField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        boolean hasIdSort = sortFields.stream()
                .anyMatch(sort -> sort != null && sort.getField() == ShowTimeField.ID);
        if (!hasIdSort) {
            sortFields.add(new SortField<>(ShowTimeField.ID, "ASC"));
        }

        List<FilterField<ShowTimeField>> filterFields = request.getFilterBy();
        long totalElements = showTimeRepositoryImpl.countWithFilter(keyword, filterFields);
        List<ShowTime> showTimes = showTimeRepositoryImpl.searchWithPageAndSortAndFilter(
                keyword, page, size, sortFields, filterFields);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        Map<UUID, PricingPolicyResponse> pricingPolicyMap = getPricingPolicyResponseMap(showTimes);

        return PageResponse.<ShowTimeResponse>builder()
                .data(showTimes.stream()
                        .map(showTime -> toShowTimeResponse(showTime, pricingPolicyMap.get(showTime.getPricingPolicyId())))
                        .collect(Collectors.toList()))
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    private PageRequest<ShowTimeField> scopeShowtimeSearchRequest(PageRequest<ShowTimeField> request,
                                                                  HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        RequestAuthUtils.requireAnyRole(
                httpRequest,
                log,
                "showtime_read_action",
                HeaderNames.ROLE_ADMIN,
                HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);

        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return request;
        }

        Set<UUID> accessibleHallIds = resolveAccessibleHallIdsByUser(httpRequest);
        if (accessibleHallIds.isEmpty()) {
            return null;
        }

        List<FilterField<ShowTimeField>> filters = new ArrayList<>();
        if (request.getFilterBy() != null) {
            filters.addAll(request.getFilterBy());
        }
        filters.add(FilterField.<ShowTimeField>builder()
                .field(ShowTimeField.HALL_ID)
                .operator("IN")
                .value(accessibleHallIds.stream().map(UUID::toString).toList())
                .build());

        return PageRequest.<ShowTimeField>builder()
                .page(request.getPage())
                .size(request.getSize())
                .keyword(request.getKeyword())
                .sortBy(request.getSortBy())
                .filterBy(filters)
                .build();
    }

    private PageResponse<ShowTimeResponse> emptyShowtimePageResponse(Integer page, Integer size) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 20 : size;
        return PageResponse.<ShowTimeResponse>builder()
                .data(List.of())
                .currentPage(currentPage)
                .totalPages(0)
                .totalElements(0)
                .size(pageSize)
                .hasNext(false)
                .hasPrevious(currentPage > 1)
                .build();
    }

    private PageResponse<ShowTimeResponse> enrichShowtimePage(PageResponse<ShowTimeResponse> base) {
        List<ShowTimeResponse> showtimes = new ArrayList<>(base.getData());
        if (showtimes.isEmpty()) {
            return base;
        }

        List<UUID> filmIds = showtimes.stream()
                .map(ShowTimeResponse::getFilmId)
                .distinct()
                .toList();

        Map<UUID, FilmResponse> filmMap = filmIds.isEmpty()
                ? Map.of()
                : filmGrpcClient.getFilmsByIds(filmIds);

        Map<UUID, HallResponse> hallMap = getHallResponseMap(showtimes);
        enrichHallCinemaNames(hallMap);
        Map<UUID, SeatGrpcClient.LayoutBundle> hallLayoutMap = getHallLayoutMap(showtimes);

        showtimes.forEach(showtime -> {
            showtime.setFilm(filmMap.get(showtime.getFilmId()));
            showtime.setHall(hallMap.get(showtime.getHallId()));
            SeatAvailabilityStats stats = resolveSeatAvailability(
                    showtime.getId(),
                    hallLayoutMap.get(showtime.getHallId()));
            showtime.setTotalSeatCapacity(stats.totalSeatCapacity());
            showtime.setOccupiedSeats(stats.occupiedSeats());
            showtime.setAvailableSeats(stats.availableSeats());
        });

        return PageResponse.<ShowTimeResponse>builder()
                .data(showtimes)
                .currentPage(base.getCurrentPage())
                .totalPages(base.getTotalPages())
                .totalElements(base.getTotalElements())
                .size(base.getSize())
                .hasNext(base.isHasNext())
                .hasPrevious(base.isHasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShowTimeResponse getShowTimeById(UUID id) {
        ShowTime showTime = showTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
        FilmResponse film = filmGrpcClient.getFilmById(showTime.getFilmId());
        PricingPolicyResponse pricingPolicy = getPricingPolicyResponse(showTime.getPricingPolicyId());
        HallResponse hall = hallGrpcClient.getHallById(showTime.getHallId());
        enrichHallCinemaName(hall);
        SeatAvailabilityStats stats = resolveSeatAvailability(
                showTime.getId(),
                seatGrpcClient.getLayoutByHallId(showTime.getHallId()));
        ShowTimeResponse response = toShowTimeResponse(showTime, pricingPolicy);
        response.setFilm(film);
        response.setHall(hall);
        response.setTotalSeatCapacity(stats.totalSeatCapacity());
        response.setOccupiedSeats(stats.occupiedSeats());
        response.setAvailableSeats(stats.availableSeats());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMapByShowtimeId(UUID showtimeId) {
        ShowTime showTime = showTimeRepository.findById(showtimeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
        PricingPolicy pricingPolicy = pricingPolicyRepository.findByIdAndIsDeletedFalse(showTime.getPricingPolicyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        SeatGrpcClient.LayoutBundle layout = seatGrpcClient.getLayoutByHallId(showTime.getHallId());
        List<String> seatCodes = layout.getSeats().stream()
                .map(com.cinema.grpc.seat.LayoutSeatPayload::getSeatCode)
                .toList();
        Map<String, String> stateMap = bookingGrpcClient.getSeatRuntimeStates(showtimeId, seatCodes);

        List<SeatMapCellResponse> cells = new ArrayList<>();
        layout.getSeats().forEach(seat -> cells.add(SeatMapCellResponse.builder()
                .kind("SEAT")
                .row(seat.getRow())
                .col(seat.getCol())
                .seatCode(seat.getSeatCode())
                .seatType(seat.getSeatType())
                .state(stateMap.getOrDefault(seat.getSeatCode().toUpperCase(), "AVAILABLE"))
                .price(resolvePriceBySeatType(seat.getSeatType(), pricingPolicy))
                .build()));
        layout.getCells().forEach(cell -> cells.add(SeatMapCellResponse.builder()
                .kind("CELL")
                .row(cell.getRow())
                .col(cell.getCol())
                .cellType(cell.getCellType())
                .state("UNAVAILABLE")
                .build()));
        cells.sort(Comparator.comparing(SeatMapCellResponse::getRow).thenComparing(SeatMapCellResponse::getCol));

        return SeatMapResponse.builder()
                .showtimeId(showtimeId)
                .hallId(showTime.getHallId())
                .totalRows(layout.getTotalRows())
                .totalCols(layout.getTotalCols())
                .screenPosition(layout.getScreenPosition())
                .cells(cells)
                .build();
    }

    @Override
    @Transactional
    public ResultResponse<ShowTimeResponse> createShowTime(
            ShowTimeCreateRequest showTimeCreateRequest,
            HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        validateCreateShowTimeRequest(showTimeCreateRequest);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        UUID hallCinemaId = validateHallInCinema(showTimeCreateRequest.getHallId(), accessibleCinemaIds);
        validatePricingPolicy(showTimeCreateRequest.getPricingPolicyId(), hallCinemaId, accessibleCinemaIds);

        FilmResponse filmResponse = filmGrpcClient.getFilmById(showTimeCreateRequest.getFilmId());
        CinemaOperatingHoursResponse cinemaOperatingHours = cinemaGrpcClient.getCinemaById(hallCinemaId);

        long duration = (long) filmResponse.getDuration() + 30;
        LocalDateTime startTime = showTimeCreateRequest.getStartDateTime();
        LocalDateTime endTime = showTimeCreateRequest.getEndDateTime();
        if (endTime.isBefore(startTime.plusMinutes(duration))) {
            throw new BusinessException(ErrorCode.INVALID_END_TIME);
        }

        ResultResponse<ShowTimeResponse> resultResponse = new ResultResponse<>();
        List<SuccessResponse<ShowTimeResponse>> showTimeResponses = new ArrayList<>();
        List<ShowTime> createdShowTimes = new ArrayList<>();

        LocalDateTime cursor = startTime;
        while (!cursor.plusMinutes(duration).isAfter(endTime)) {
            cursor = normalizeToOperatingWindow(
                    cursor,
                    cinemaOperatingHours.getOpenTime(),
                    cinemaOperatingHours.getCloseTime());

            LocalDateTime slotEnd = cursor.plusMinutes(duration);

            if (slotEnd.isAfter(endTime)) {
                break;
            }

            Optional<ShowTime> overlapping = showTimeRepository.findOverlapping(
                    showTimeCreateRequest.getHallId(),
                    cursor,
                    slotEnd);

            if (overlapping.isPresent()) {
                cursor = overlapping.get().getEndDateTime();
                continue;
            }

            ShowTimeCreateRequest slotRequest = copyCreateRequestWithWindow(showTimeCreateRequest, cursor, slotEnd);
            ShowTime showTime = showTimeMapper.toEntity(slotRequest);
            showTime = showTimeRepository.save(showTime);
            createdShowTimes.add(showTime);
            cursor = slotEnd;
        }

        if (createdShowTimes.isEmpty()) {
            throw new BusinessException(ErrorCode.ALL_TIME_SLOT_OCCUPIED);
        }

        Map<UUID, PricingPolicyResponse> pricingPolicyMap = getPricingPolicyResponseMap(createdShowTimes);
        for (ShowTime showTime : createdShowTimes) {
            showTimeResponses.add(new SuccessResponse<>(
                    toShowTimeResponse(showTime, pricingPolicyMap.get(showTime.getPricingPolicyId()))));
        }

        resultResponse.setSuccessResponse(showTimeResponses);
        return resultResponse;
    }

    @Override
    @Transactional
    public ActionMessageResponse updateShowTime(
            UUID id,
            UpdateShowTimeRequest updateShowTimeRequest,
            HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);

        ShowTime showTime = getEditableShowTime(id);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        validateShowtimeAccess(showTime, accessibleCinemaIds);
        UUID hallCinemaId = validateHallInCinema(showTime.getHallId(), accessibleCinemaIds);
        validatePricingPolicy(updateShowTimeRequest.getPricingPolicyId(), hallCinemaId, accessibleCinemaIds);

        CinemaOperatingHoursResponse cinemaOperatingHours = cinemaGrpcClient.getCinemaById(hallCinemaId);
        FilmResponse filmResponse = filmGrpcClient.getFilmById(showTime.getFilmId());
        long duration = (long) filmResponse.getDuration() + 30;
        LocalDateTime requestedStartTime = updateShowTimeRequest.getStartDateTime();
        LocalDateTime requestedEndTime = updateShowTimeRequest.getEndDateTime();

        if (requestedEndTime.isBefore(requestedStartTime.plusMinutes(duration))) {
            throw new BusinessException(ErrorCode.INVALID_END_TIME);
        }

        if (!isStartWithinOperatingWindow(
                requestedStartTime,
                cinemaOperatingHours.getOpenTime(),
                cinemaOperatingHours.getCloseTime())) {
            throw new BusinessException(ErrorCode.INVALID_END_TIME);
        }

        Optional<ShowTime> overlapping = showTimeRepository.findOverlappingExcludingId(
                showTime.getId(),
                showTime.getHallId(),
                requestedStartTime,
                requestedEndTime);

        if (overlapping.isPresent()) {
            throw new BusinessException(ErrorCode.ALL_TIME_SLOT_OCCUPIED);
        }

        showTime.setPricingPolicyId(updateShowTimeRequest.getPricingPolicyId());
        showTime.setStartDateTime(requestedStartTime);
        showTime.setEndDateTime(requestedEndTime);
        showTime.setStatus(updateShowTimeRequest.getStatus());
        showTimeRepository.save(showTime);
        return ActionMessageResponse.builder()
                .message("Cáº­p nháº­t suáº¥t chiáº¿u thÃ nh cÃ´ng")
                .build();
    }
    @Override
    @Transactional
    public ActionMessageResponse deleteShowTime(UUID id, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);

        ShowTime showTime = showTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
        validateShowtimeAccess(showTime, resolveAccessibleCinemaIdsByUser(httpRequest));

        if (bookingGrpcClient.isShowtimeBooked(id)) {
            throw new BusinessException(ErrorCode.NOT_UPDATE_BOOKED_SHOWTIME);
        }

        if (showTime.getIsDeleted()) {
            throw new BusinessException(ErrorCode.DELETED_SHOWTIME);
        }

        showTime.setIsDeleted(true);
        showTimeRepository.save(showTime);
        return ActionMessageResponse.builder()
                .message("XÃ³a suáº¥t chiáº¿u thÃ nh cÃ´ng")
                .build();
    }

    private ShowTimeResponse toShowTimeResponse(ShowTime showTime, PricingPolicyResponse pricingPolicy) {
        ShowTimeResponse response = showTimeMapper.toResponse(showTime);
        response.setPricingPolicy(pricingPolicy);
        return response;
    }

    private ShowTime getEditableShowTime(UUID id) {
        ShowTime showTime = showTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
        if (showTime.getIsDeleted()) {
            throw new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND);
        }

        if (bookingGrpcClient.isShowtimeBooked(id)) {
            throw new BusinessException(ErrorCode.NOT_UPDATE_BOOKED_SHOWTIME);
        }

        if (showTime.getStatus().equals(ShowTimeEnum.ShowTimeStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.NOT_UPDATE_CANCELLED_SHOWTIME);
        }

        if (showTime.getStatus().equals(ShowTimeEnum.ShowTimeStatus.FINISHED)) {
            throw new BusinessException(ErrorCode.NOT_UPDATE_FINISHED_SHOWTIME);
        }
        return showTime;
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER, log, "showtime_manager_action");
    }

    private void validateCreateShowTimeRequest(ShowTimeCreateRequest request) {
        if (request == null
                || request.getStartDateTime() == null
                || request.getEndDateTime() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        if (!request.getStartDateTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private LocalDateTime normalizeToOperatingWindow(LocalDateTime cursor, LocalTime openTime, LocalTime closeTime) {
        if (cursor == null || openTime == null || closeTime == null) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }

        LocalDateTime dayOpen = cursor.toLocalDate().atTime(openTime);
        if (cursor.isBefore(dayOpen)) {
            return dayOpen;
        }

        if (cursor.toLocalTime().isAfter(closeTime)) {
            return cursor.toLocalDate().plusDays(1).atTime(openTime);
        }

        return cursor;
    }

    private List<FilterField<ShowTimeField>> buildActiveShowtimeStatusFilters() {
        return List.of(FilterField.<ShowTimeField>builder()
                .field(ShowTimeField.STATUS)
                .operator("IN")
                .value(List.of(
                        ShowTimeEnum.ShowTimeStatus.SCHEDULED,
                        ShowTimeEnum.ShowTimeStatus.ONGOING))
                .build());
    }

    private boolean isStartWithinOperatingWindow(
            LocalDateTime startDateTime,
            LocalTime openTime,
            LocalTime closeTime) {
        if (startDateTime == null || openTime == null || closeTime == null) {
            throw new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR);
        }

        LocalTime startTime = startDateTime.toLocalTime();
        return !startTime.isBefore(openTime) && !startTime.isAfter(closeTime);
    }

    private ShowTimeCreateRequest copyCreateRequestWithWindow(ShowTimeCreateRequest source,
                                                             LocalDateTime startDateTime,
                                                             LocalDateTime endDateTime) {
        return ShowTimeCreateRequest.builder()
                .hallId(source.getHallId())
                .filmId(source.getFilmId())
                .pricingPolicyId(source.getPricingPolicyId())
                .startDateTime(startDateTime)
                .endDateTime(endDateTime)
                .status(source.getStatus())
                .build();
    }

    private void validatePricingPolicy(UUID pricingPolicyId, UUID cinemaId, Set<UUID> accessibleCinemaIds) {
        PricingPolicy pricingPolicy = pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!cinemaId.equals(pricingPolicy.getCinemaId())) {
            throw new BusinessException(ErrorCode.PRICING_POLICY_NOT_IN_CINEMA);
        }
        if (!accessibleCinemaIds.contains(pricingPolicy.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private UUID validateHallInCinema(UUID hallId, Set<UUID> accessibleCinemaIds) {
        UUID hallCinemaId = hallGrpcClient.getCinemaIdByHallId(hallId);
        if (!accessibleCinemaIds.contains(hallCinemaId)) {
            throw new BusinessException(ErrorCode.HALL_NOT_IN_CINEMA);
        }
        return hallCinemaId;
    }

    private void validateShowtimeAccess(ShowTime showTime, Set<UUID> accessibleCinemaIds) {
        UUID hallCinemaId = hallGrpcClient.getCinemaIdByHallId(showTime.getHallId());
        if (!accessibleCinemaIds.contains(hallCinemaId)) {
            throw new BusinessException(ErrorCode.HALL_NOT_IN_CINEMA);
        }
    }

    private PricingPolicyResponse getPricingPolicyResponse(UUID pricingPolicyId) {
        return pricingPolicyRepository.findById(pricingPolicyId)
                .map(pricingPolicyMapper::toResponse)
                .orElse(null);
    }

    private Map<UUID, PricingPolicyResponse> getPricingPolicyResponseMap(List<ShowTime> showTimes) {
        List<UUID> pricingPolicyIds = showTimes.stream()
                .map(ShowTime::getPricingPolicyId)
                .distinct()
                .collect(Collectors.toList());

        return pricingPolicyIds.isEmpty()
                ? Map.of()
                : pricingPolicyRepository.findAllById(pricingPolicyIds).stream()
                .collect(Collectors.toMap(PricingPolicy::getId, pricingPolicyMapper::toResponse));
    }

    private Map<UUID, HallResponse> getHallResponseMap(List<ShowTimeResponse> showtimes) {
        Map<UUID, HallResponse> hallMap = new HashMap<>();
        for (UUID hallId : showtimes.stream().map(ShowTimeResponse::getHallId).distinct().toList()) {
            hallMap.put(hallId, hallGrpcClient.getHallById(hallId));
        }
        return hallMap;
    }

    private void enrichHallCinemaNames(Map<UUID, HallResponse> hallMap) {
        if (hallMap == null || hallMap.isEmpty()) {
            return;
        }

        Map<UUID, String> cinemaNameCache = new HashMap<>();
        hallMap.values().forEach(hall -> enrichHallCinemaName(hall, cinemaNameCache));
    }

    private void enrichHallCinemaName(HallResponse hall) {
        enrichHallCinemaName(hall, new HashMap<>());
    }

    private void enrichHallCinemaName(HallResponse hall, Map<UUID, String> cinemaNameCache) {
        if (hall == null || hall.getCinemaId() == null) {
            return;
        }

        hall.setCinemaName(resolveCinemaName(hall.getCinemaId(), cinemaNameCache));
    }

    private String resolveCinemaName(UUID cinemaId, Map<UUID, String> cinemaNameCache) {
        if (cinemaId == null) {
            return null;
        }

        if (cinemaNameCache.containsKey(cinemaId)) {
            return cinemaNameCache.get(cinemaId);
        }

        try {
            String cinemaName = cinemaGrpcClient.getCinemaNameById(cinemaId);
            cinemaNameCache.put(cinemaId, cinemaName);
            return cinemaName;
        } catch (BusinessException ex) {
            log.warn("Cannot enrich cinema name for hall response. cinemaId={}, errorCode={}",
                    cinemaId, ex.getErrorCode());
            cinemaNameCache.put(cinemaId, null);
            return null;
        }
    }

    private Map<UUID, SeatGrpcClient.LayoutBundle> getHallLayoutMap(List<ShowTimeResponse> showtimes) {
        Map<UUID, SeatGrpcClient.LayoutBundle> hallLayoutMap = new HashMap<>();
        for (UUID hallId : showtimes.stream().map(ShowTimeResponse::getHallId).distinct().toList()) {
            hallLayoutMap.put(hallId, seatGrpcClient.getLayoutByHallId(hallId));
        }
        return hallLayoutMap;
    }

    private SeatAvailabilityStats resolveSeatAvailability(UUID showtimeId, SeatGrpcClient.LayoutBundle layout) {
        if (layout == null || layout.getSeats() == null || layout.getSeats().isEmpty()) {
            return new SeatAvailabilityStats(0, 0, 0);
        }

        List<String> seatCodes = layout.getSeats().stream()
                .map(com.cinema.grpc.seat.LayoutSeatPayload::getSeatCode)
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .toList();
        if (seatCodes.isEmpty()) {
            return new SeatAvailabilityStats(0, 0, 0);
        }

        Map<String, String> seatStates = bookingGrpcClient.getSeatRuntimeStates(showtimeId, seatCodes);
        int totalSeatCapacity = seatCodes.size();
        int occupiedSeats = (int) seatStates.values().stream()
                .filter(state -> state != null && !"AVAILABLE".equalsIgnoreCase(state))
                .count();
        int availableSeats = Math.max(0, totalSeatCapacity - occupiedSeats);
        return new SeatAvailabilityStats(totalSeatCapacity, occupiedSeats, availableSeats);
    }

    private Set<UUID> resolveAccessibleCinemaIdsByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER);
            return new HashSet<>(cinemaIds);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_FORMAT) {
                log.warn("Invalid auth headers method={} path={}", httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            throw ex;
        }
    }

    private Set<UUID> resolveAccessibleHallIdsByUser(HttpServletRequest httpRequest) {
        UUID userId = RequestAuthUtils.requireUserId(httpRequest);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
        if (cinemaIds == null || cinemaIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> hallIds = new HashSet<>();
        for (UUID cinemaId : cinemaIds) {
            if (cinemaId == null) {
                continue;
            }
            hallIds.addAll(hallGrpcClient.listActiveHallIdsByCinema(cinemaId));
        }
        return hallIds;
    }

    private Long resolvePriceBySeatType(String seatType, PricingPolicy pricingPolicy) {
        if (seatType == null) {
            return pricingPolicy.getStandardPrice();
        }
        return switch (seatType.trim().toUpperCase()) {
            case "VIP" -> pricingPolicy.getVipPrice();
            case "COUPLE" -> pricingPolicy.getCouplePrice();
            default -> pricingPolicy.getStandardPrice();
        };
    }

    private record SeatAvailabilityStats(int totalSeatCapacity, int occupiedSeats, int availableSeats) {
    }
}
