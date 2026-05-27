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
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public PageResponse<ShowTimeResponse> searchShowtimes(PageRequest<ShowTimeField> request) {
        return enrichShowtimePage(searchShowtimesBase(request));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShowTimeResponse> getActiveShowtimesByFilmId(UUID filmId, Integer page, Integer size) {
        PageRequest<ShowTimeField> request = PageRequest.<ShowTimeField>builder()
                .page(page)
                .size(size)
                .filterBy(List.of(FilterField.<ShowTimeField>builder()
                        .field(ShowTimeField.FILM_ID)
                        .operator("EQ")
                        .value(filmId.toString())
                        .build()))
                .build();
        return enrichShowtimePage(searchShowtimesBase(request));
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

    private PageResponse<ShowTimeResponse> enrichShowtimePage(PageResponse<ShowTimeResponse> base) {
        List<ShowTimeResponse> showtimes = new ArrayList<>(base.getData());

        List<UUID> filmIds = showtimes.stream()
                .map(ShowTimeResponse::getFilmId)
                .distinct()
                .toList();

        Map<UUID, FilmResponse> filmMap = filmIds.isEmpty()
                ? Map.of()
                : filmGrpcClient.getFilmsByIds(filmIds);

        Map<UUID, HallResponse> hallMap = getHallResponseMap(showtimes);

        showtimes.forEach(showtime -> {
            showtime.setFilm(filmMap.get(showtime.getFilmId()));
            showtime.setHall(hallMap.get(showtime.getHallId()));
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
        ShowTimeResponse response = toShowTimeResponse(showTime, pricingPolicy);
        response.setFilm(film);
        response.setHall(hall);
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
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        UUID hallCinemaId = validateHallInCinema(showTimeCreateRequest.getHallId(), accessibleCinemaIds);
        validatePricingPolicy(showTimeCreateRequest.getPricingPolicyId(), hallCinemaId, accessibleCinemaIds);

        FilmResponse filmResponse = filmGrpcClient.getFilmById(showTimeCreateRequest.getFilmId());

        long duration = (long) filmResponse.getDuration() + 30;
        LocalDateTime startTime = showTimeCreateRequest.getStartDateTime();
        LocalDateTime endTime = showTimeCreateRequest.getEndDateTime();

        if (endTime.isBefore(startTime.plusMinutes(duration))) {
            throw new BusinessException(ErrorCode.INVALID_END_TIME);
        }

        ResultResponse<ShowTimeResponse> resultResponse = new ResultResponse<>();
        List<SuccessResponse<ShowTimeResponse>> showTimeResponses = new ArrayList<>();
        List<ShowTime> createdShowTimes = new ArrayList<>();

        while (startTime.plusMinutes(duration).isBefore(endTime)) {
            Optional<ShowTime> overlapping = showTimeRepository.findOverlapping(
                    showTimeCreateRequest.getHallId(),
                    startTime,
                    startTime.plusMinutes(duration));

            if (overlapping.isEmpty()) {
                showTimeCreateRequest.setStartDateTime(startTime);
                showTimeCreateRequest.setEndDateTime(startTime.plusMinutes(duration));
                ShowTime showTime = showTimeMapper.toEntity(showTimeCreateRequest);
                showTime = showTimeRepository.save(showTime);
                createdShowTimes.add(showTime);
                startTime = startTime.plusMinutes(duration);
                continue;
            }

            startTime = overlapping.get().getEndDateTime();
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

        FilmResponse filmResponse = filmGrpcClient.getFilmById(showTime.getFilmId());
        long duration = (long) filmResponse.getDuration() + 30;
        LocalDateTime requestedStartTime = updateShowTimeRequest.getStartDateTime();
        LocalDateTime requestedEndTime = updateShowTimeRequest.getEndDateTime();

        if (requestedEndTime.isBefore(requestedStartTime.plusMinutes(duration))) {
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
    public ActionMessageResponse updateShowTimeStatus(
            UUID id,
            UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
            HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);

        ShowTime showTime = getEditableShowTime(id);
        validateShowtimeAccess(showTime, resolveAccessibleCinemaIdsByUser(httpRequest));
        showTime.setStatus(updateShowTimeStatusRequest.getStatus());
        showTimeRepository.save(showTime);
        return ActionMessageResponse.builder()
                .message("Cáº­p nháº­t tráº¡ng thÃ¡i suáº¥t chiáº¿u thÃ nh cÃ´ng")
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

    private Set<UUID> resolveAccessibleCinemaIdsByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER);
            if (cinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            return new HashSet<>(cinemaIds);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_FORMAT) {
                log.warn("Invalid auth headers method={} path={}", httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            throw ex;
        }
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
}
