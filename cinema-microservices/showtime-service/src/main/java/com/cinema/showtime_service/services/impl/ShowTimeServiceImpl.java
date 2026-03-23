package com.cinema.showtime_service.services.impl;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.ResultResponse;
import com.cinema.dto.response.SuccessResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeSortField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeStatusRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
import com.cinema.showtime_service.dto.response.HallResponse;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.mapper.ShowTimeMapper;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import com.cinema.showtime_service.services.ShowTimeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShowTimeServiceImpl implements ShowTimeService {

    final ShowTimeRepository showTimeRepository;
    final ShowTimeMapper showTimeMapper;
    final RestTemplate restTemplate;

    @Value("${film-service.url}")
    String filmUrl;

    @Value("${hall-service.url}")
    String hallUrl;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public CursorPageResponse<ShowTimeResponse> getAllShowtimes(
            CursorPageRequest<ShowTimeSortField> request) {

        // Lọc trạng thái SCHEDULED, ONGOING và isDeleted = false
        var allowedStatuses = java.util.List.of(
                com.cinema.Enum.ShowTimeEnum.ShowTimeStatus.SCHEDULED,
                com.cinema.Enum.ShowTimeEnum.ShowTimeStatus.ONGOING);

        UUID cursor = request.getParsedCursor();
        String keyword = request.getNormalizedKeyword();
        int size = request.getSize();
        // Lấy danh sách sort fields
        var sortFields = request.getSortFields(); // cần bổ sung hàm getSortFields nếu chưa có

        // Query động bằng JPA Specification hoặc native query, ở đây demo native query
        // đơn giản
        // Giả sử có method findByCursorAndStatusAndIsDeletedAndKeywordAndSortMulti
        java.util.List<ShowTime> showtimes = showTimeRepository.findByCursorAndStatusAndIsDeletedAndKeywordAndSortMulti(
                cursor, allowedStatuses, false, keyword, size + 1, sortFields);

        boolean hasNext = showtimes.size() > size;
        if (hasNext)
            showtimes = showtimes.subList(0, size);
        String nextCursor = hasNext ? showtimes.get(showtimes.size() - 1).getId().toString() : null;

        return com.cinema.dto.response.CursorPageResponse.<ShowTimeResponse>builder()
                .data(showtimes.stream().map(showTimeMapper::toResponse).toList())
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .size(showtimes.size())
                .build();
    }

    @Override
    public ResultResponse<ShowTimeResponse> createShowTime(ShowTimeCreateRequest showTimeCreateRequest,
                                                           HttpServletRequest httpRequest) {

        String role = httpRequest.getHeader("X-User-Role");
        if (!"MANAGER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        FilmResponse filmResponse;
        try {
            CompletableFuture<FilmResponse> filmFuture = CompletableFuture
                    .supplyAsync(() -> fetchFilmsByIds(showTimeCreateRequest.getFilmId()));
            System.out.println(filmFuture);
            filmResponse = filmFuture.join();

            // CompletableFuture<HallResponse> hallFuture = CompletableFuture
            // .supplyAsync(() -> fetchHallsByIds(showTimeCreateRequest.getHallId()));

            // HallResponse hallResponse = hallFuture.join();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOT_CREATED_SHOWTIME);
        }

        // Lấy thời gian ra và kiểm tra thời gian và thời lượng của phim ra kiểm tra
        // Thời lượng của phim + 30p dọn dẹp vệ sinh sau khi xem phim xong
        long duration = (long) filmResponse.getDuration() + 30;
        LocalDateTime startTime = showTimeCreateRequest.getStartDateTime();
        LocalDateTime endTime = showTimeCreateRequest.getEndDateTime();

        // Check endTime người dùng truyền vào có hợp lệ không
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
                // Gán lại thời gian tạo mới phim
                showTimeCreateRequest.setStartDateTime(startTime);
                showTimeCreateRequest.setEndDateTime(startTime.plusMinutes(duration));
                ShowTime showTime = showTimeMapper.toEntity(showTimeCreateRequest);
                showTime = showTimeRepository.save(showTime);
                showTimeResponses
                        .add(new SuccessResponse<>(showTimeMapper.toResponse(showTime)));
                // Gán lại startTime
                startTime = startTime.plusMinutes(duration);
                continue;
            }

            // Dời startTime = endTime của show đang chiếm slot
            startTime = overlapping.get().getEndDateTime();
        }
        if (showTimeResponses.isEmpty()) {
            throw new BusinessException(ErrorCode.ALL_TIME_SLOT_OCCUPIED);
        }
        resultResponse.setSuccessResponse(showTimeResponses);
        return resultResponse;
    }

    @Override
    public ShowTimeResponse updateShowTimeStatus(UUID id, UpdateShowTimeStatusRequest updateShowTimeStatusRequest,
                                                 HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader("X-User-Role");
        if (!"MANAGER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        ShowTime showTime = showTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));
        if (showTime.getIsDeleted()) {
            throw new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND);
        }

        if (showTime.getIsBooked()) {
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
        String role = httpRequest.getHeader("X-User-Role");
        if (!"MANAGER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        ShowTime showTime = showTimeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOWTIME_NOT_FOUND));

        if (showTime.getIsBooked()) {
            throw new BusinessException(ErrorCode.NOT_UPDATE_BOOKED_SHOWTIME);
        }

        if (showTime.getIsDeleted()) {
            throw new BusinessException(ErrorCode.DELETED_SHOWTIME);
        }
        showTime.setIsDeleted(true);
        showTimeRepository.save(showTime);
    }

    // Gọi Film Service để lấy thông tin phim dựa trên filmId
    private FilmResponse fetchFilmsByIds(UUID filmId) {

        String url = filmUrl + "/{filmId}";
        try {
            ResponseEntity<APIResponse<FilmResponse>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    },
                    filmId);
            if (response.getBody() == null || !response.getBody().isSuccess()) {
                log.error("Failed to fetch film with ID {}: {}", filmId,
                        response.getBody() != null ? response.getBody().getMessage() : "No response body");
                throw new BusinessException(ErrorCode.FILM_NOT_FOUND);
            }
            log.info("Fetched film with ID {}: {}", filmId, response.getBody().getData());
            return response.getBody().getData();
        } catch (RestClientException e) {
            log.error("Error fetching films from Film Service: {}", e.getMessage());
            throw new BusinessException(ErrorCode.FILM_NOT_FOUND);
        }
    }

    // private HallResponse fetchHallsByIds(UUID hallId) {
    // String url = hallUrl + "/{hallId}";
    // try {
    // ResponseEntity<APIResponse<HallResponse>> response = restTemplate.exchange(
    // url,
    // HttpMethod.GET,
    // HttpEntity.EMPTY,
    // new ParameterizedTypeReference<>() {
    // },
    // hallId);
    // if (response.getBody() == null || !response.getBody().isSuccess()) {
    // log.error("Failed to fetch hall with ID {}: {}", hallId,
    // response.getBody() != null ? response.getBody().getMessage() : "No response
    // body");
    // throw new BusinessException(ErrorCode.HALL_NOT_FOUND);
    // }
    // return response.getBody().getData();
    // } catch (RestClientException e) {
    // log.error("Error fetching halls from Hall Service: {}", e.getMessage());
    // throw new BusinessException(ErrorCode.HALL_SERVICE_ERROR);
}
