package com.cinema.showtime_service.services.impl;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.dto.response.SuccessResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
import com.cinema.showtime_service.dto.response.HallResponse;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.grpc.BookingGrpcClient;
import com.cinema.showtime_service.grpc.CinemaGrpcClient;
import com.cinema.showtime_service.grpc.FilmGrpcClient;
import com.cinema.showtime_service.grpc.HallGrpcClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    PricingPolicyRepository pricingPolicyRepository;

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ShowTimeResponse> searchShowtimes(CursorPageRequest<ShowTimeField> request) {
        CursorPageResponse<ShowTimeResponse> base = searchShowtimesBase(request);
        List<ShowTimeResponse> showtimes = new ArrayList<>(base.getData());

        List<UUID> filmIds = showtimes.stream()
                .map(ShowTimeResponse::getFilmId)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, FilmResponse> filmMap = filmIds.isEmpty()
                ? Map.of()
                : filmGrpcClient.getFilmsByIds(filmIds);

        Map<UUID, HallResponse> hallMap = getHallResponseMap(showtimes);

        showtimes.forEach(showtime -> {
            showtime.setFilm(filmMap.get(showtime.getFilmId()));
            showtime.setHall(hallMap.get(showtime.getHallId()));
        });

        return CursorPageResponse.<ShowTimeResponse>builder()
                .data(showtimes)
                .nextCursor(base.getNextCursor())
                .prevCursor(base.getPrevCursor())
                .hasNext(base.isHasNext())
                .size(base.getSize())
                .build();
    }

    private CursorPageResponse<ShowTimeResponse> searchShowtimesBase(CursorPageRequest<ShowTimeField> request) {
        log.info("Lay danh sach showtime (cursor={}, size={}, keyword={}, sortBy={}, filterBy={})",
                request.getCursor(), request.getSize(), request.getKeyword(), request.getSortBy(),
                request.getFilterBy());

        String[] cursorParts = request.getParsedCompositeCursor();
        String keyword = request.getNormalizedKeyword();
        int size = request.getSizeOrDefault();

        List<SortField<ShowTimeField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        sortFields.add(new SortField<>(ShowTimeField.ID, "ASC"));

        List<FilterField<ShowTimeField>> filterFields = request.getFilterBy();
        List<ShowTime> showTimes = showTimeRepositoryImpl.searchWithCursorAndSortAndFilter(
                cursorParts, keyword, size, sortFields, filterFields);

        boolean hasNext = showTimes.size() > size;
        String nextCursor = null;
        if (hasNext) {
            showTimes = showTimes.subList(0, size);
            nextCursor = CursorPageRequest.encodeCompositeCursor(
                    ShowTimeField.getFieldValues(showTimes.get(showTimes.size() - 1), sortFields));
        }

        String prevCursor = null;
        if (cursorParts != null && cursorParts.length > 0) {
            List<ShowTime> prevShowTimes = showTimeRepositoryImpl.previousCursor(
                    cursorParts, keyword, size, sortFields, filterFields);
            if (!prevShowTimes.isEmpty() && prevShowTimes.size() == size) {
                prevCursor = CursorPageRequest.encodeCompositeCursor(
                        ShowTimeField.getFieldValues(prevShowTimes.get(size - 1), sortFields));
            }
        }

        Map<UUID, PricingPolicyResponse> pricingPolicyMap = getPricingPolicyResponseMap(showTimes);

        return CursorPageResponse.<ShowTimeResponse>builder()
                .data(showTimes.stream()
                        .map(showTime -> toShowTimeResponse(showTime, pricingPolicyMap.get(showTime.getPricingPolicyId())))
                        .collect(Collectors.toList()))
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasNext(hasNext)
                .size(showTimes.size())
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
    @Transactional
    public ResultResponse<ShowTimeResponse> createShowTime(
            ShowTimeCreateRequest showTimeCreateRequest,
            HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        validateHallInCinema(showTimeCreateRequest.getHallId(), cinemaId);
        validatePricingPolicy(showTimeCreateRequest.getPricingPolicyId(), cinemaId);

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
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        validateHallInCinema(showTime.getHallId(), cinemaId);
        validatePricingPolicy(updateShowTimeRequest.getPricingPolicyId(), cinemaId);

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
                .message("Cập nhật suất chiếu thành công")
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
        showTime.setStatus(updateShowTimeStatusRequest.getStatus());
        showTimeRepository.save(showTime);
        return ActionMessageResponse.builder()
                .message("Cập nhật trạng thái suất chiếu thành công")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteShowTime(UUID id, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);

        ShowTime showTime = showTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));

        if (bookingGrpcClient.isShowtimeBooked(id)) {
            throw new BusinessException(ErrorCode.NOT_UPDATE_BOOKED_SHOWTIME);
        }

        if (showTime.getIsDeleted()) {
            throw new BusinessException(ErrorCode.DELETED_SHOWTIME);
        }

        showTime.setIsDeleted(true);
        showTimeRepository.save(showTime);
        return ActionMessageResponse.builder()
                .message("Xóa suất chiếu thành công")
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
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!HeaderNames.ROLE_MANAGER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePricingPolicy(UUID pricingPolicyId, UUID cinemaId) {
        PricingPolicy pricingPolicy = pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!cinemaId.equals(pricingPolicy.getCinemaId())) {
            throw new BusinessException(ErrorCode.PRICING_POLICY_NOT_IN_CINEMA);
        }
    }

    private void validateHallInCinema(UUID hallId, UUID cinemaId) {
        UUID hallCinemaId = hallGrpcClient.getCinemaIdByHallId(hallId);
        if (!cinemaId.equals(hallCinemaId)) {
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

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        String userIdRaw = httpRequest.getHeader(HeaderNames.X_USER_ID);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return cinemaGrpcClient.getCinemaIdByUserId(UUID.fromString(userIdRaw));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
    }
}
