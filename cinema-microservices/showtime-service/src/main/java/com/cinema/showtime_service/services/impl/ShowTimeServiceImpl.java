package com.cinema.showtime_service.services.impl;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.dto.response.SuccessResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.dto.response.ShowTimeWithFilmResponse;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.grpc.BookingGrpcClient;
import com.cinema.showtime_service.grpc.FilmGrpcClient;
import com.cinema.showtime_service.mapper.ShowTimeMapper;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShowTimeServiceImpl implements ShowTimeService {

    ShowTimeRepository showTimeRepository;
    ShowTimeRepositoryImpl showTimeRepositoryImpl;
    ShowTimeMapper showTimeMapper;
    FilmGrpcClient filmGrpcClient;
    BookingGrpcClient bookingGrpcClient;

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ShowTimeResponse> searchShowtimes(CursorPageRequest<ShowTimeField> request) {
        log.info("Lấy danh sách showtime (cursor={}, size={}, keyword={}, sortBy={}, filterBy={})",
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

        return CursorPageResponse.<ShowTimeResponse>builder()
                .data(showTimes.stream()
                        .map(showTimeMapper::toResponse)
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
        return showTimeMapper.toResponse(showTime);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowTimeWithFilmResponse getShowTimeByIdWithFilm(UUID id) {
        ShowTime showTime = showTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
        FilmResponse film = filmGrpcClient.getFilmById(showTime.getFilmId());
        return toWithFilmResponse(showTime, film);
    }

    @Override
    public ResultResponse<ShowTimeResponse> createShowTime(
            ShowTimeCreateRequest showTimeCreateRequest,
            HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!"MANAGER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        FilmResponse filmResponse = filmGrpcClient.getFilmById(showTimeCreateRequest.getFilmId());

        long duration = (long) filmResponse.getDuration() + 30;
        LocalDateTime startTime = showTimeCreateRequest.getStartDateTime();
        LocalDateTime endTime = showTimeCreateRequest.getEndDateTime();

        if (endTime.isBefore(startTime.plusMinutes(duration))) {
            throw new BusinessException(ErrorCode.INVALID_END_TIME);
        }

        ResultResponse<ShowTimeResponse> resultResponse = new ResultResponse<>();
        List<SuccessResponse<ShowTimeResponse>> showTimeResponses = new ArrayList<>();

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
                showTimeResponses.add(new SuccessResponse<>(showTimeMapper.toResponse(showTime)));
                startTime = startTime.plusMinutes(duration);
                continue;
            }

            startTime = overlapping.get().getEndDateTime();
        }

        if (showTimeResponses.isEmpty()) {
            throw new BusinessException(ErrorCode.ALL_TIME_SLOT_OCCUPIED);
        }

        resultResponse.setSuccessResponse(showTimeResponses);
        return resultResponse;
    }

    @Override
    public ShowTimeResponse updateShowTimeStatus(
            UUID id,
            UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
            HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!"MANAGER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

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

        showTime.setStatus(updateShowTimeStatusRequest.getStatus());
        showTimeRepository.save(showTime);
        return showTimeMapper.toResponse(showTime);
    }

    @Override
    public void deleteShowTime(UUID id, HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!"MANAGER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

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
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ShowTimeWithFilmResponse> searchShowtimesWithFilm(
            CursorPageRequest<ShowTimeField> request) {
        CursorPageResponse<ShowTimeResponse> base = searchShowtimes(request);
        List<ShowTimeResponse> showtimes = base.getData();

        List<UUID> filmIds = showtimes.stream()
                .map(ShowTimeResponse::getFilmId)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, FilmResponse> filmMap = filmIds.isEmpty()
                ? Map.of()
                : filmGrpcClient.getFilmsByIds(filmIds);

        List<ShowTimeWithFilmResponse> data = showtimes.stream()
                .map(showtime -> toWithFilmResponse(showtime, filmMap.get(showtime.getFilmId())))
                .collect(Collectors.toList());

        return CursorPageResponse.<ShowTimeWithFilmResponse>builder()
                .data(data)
                .nextCursor(base.getNextCursor())
                .prevCursor(base.getPrevCursor())
                .hasNext(base.isHasNext())
                .size(base.getSize())
                .build();
    }

    private ShowTimeWithFilmResponse toWithFilmResponse(ShowTimeResponse showtime, FilmResponse film) {
        return ShowTimeWithFilmResponse.builder()
                .id(showtime.getId())
                .hallId(showtime.getHallId())
                .filmId(showtime.getFilmId())
                .film(film)
                .startDateTime(showtime.getStartDateTime())
                .endDateTime(showtime.getEndDateTime())
                .status(showtime.getStatus())
                .isDeleted(showtime.isDeleted())
                .timeCreated(showtime.getTimeCreated())
                .timeUpdated(showtime.getTimeUpdated())
                .build();
    }

    private ShowTimeWithFilmResponse toWithFilmResponse(ShowTime showtime, FilmResponse film) {
        return ShowTimeWithFilmResponse.builder()
                .id(showtime.getId())
                .hallId(showtime.getHallId())
                .filmId(showtime.getFilmId())
                .film(film)
                .startDateTime(showtime.getStartDateTime())
                .endDateTime(showtime.getEndDateTime())
                .status(showtime.getStatus())
                .isDeleted(Boolean.TRUE.equals(showtime.getIsDeleted()))
                .timeCreated(showtime.getTimeCreated())
                .timeUpdated(showtime.getTimeUpdated())
                .build();
    }
}
