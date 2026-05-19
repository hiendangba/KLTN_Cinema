package com.cinema.hall_service.services.impl;

import com.cinema.Enum.HallEnum;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.hall_service.config.RedisConfig;
import com.cinema.hall_service.dto.request.AddHallImageRequest;
import com.cinema.hall_service.dto.request.HallCreateRequest;
import com.cinema.hall_service.dto.request.HallField;
import com.cinema.hall_service.dto.request.HallLayoutDefinitionRequest;
import com.cinema.hall_service.dto.request.ReplaceHallSeatsRequest;
import com.cinema.hall_service.dto.request.SeatUpsertRequest;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.dto.request.UpdateHallStatusRequest;
import com.cinema.hall_service.dto.response.CinemaResponse;
import com.cinema.hall_service.dto.response.HallImageResponse;
import com.cinema.hall_service.dto.response.HallResponse;
import com.cinema.hall_service.entity.Hall;
import com.cinema.hall_service.entity.HallImage;
import com.cinema.hall_service.entity.Seat;
import com.cinema.hall_service.grpc.BookingGrpcClient;
import com.cinema.hall_service.grpc.CinemaGrpcClient;
import com.cinema.hall_service.grpc.SeatGrpcClient;
import com.cinema.hall_service.grpc.ShowtimeGrpcClient;
import com.cinema.hall_service.mapper.HallImageMapper;
import com.cinema.hall_service.mapper.HallMapper;
import com.cinema.hall_service.mapper.SeatMapper;
import com.cinema.hall_service.repository.HallImageRepository;
import com.cinema.hall_service.repository.HallRepository;
import com.cinema.hall_service.repository.HallRepositoryImpl;
import com.cinema.hall_service.repository.SeatRepository;
import com.cinema.hall_service.services.HallService;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HallServiceImpl implements HallService {

    HallRepository hallRepository;
    HallRepositoryImpl hallRepositoryImpl;
    SeatRepository seatRepository;
    HallImageRepository hallImageRepository;
    HallMapper hallMapper;
    SeatMapper seatMapper;
    HallImageMapper hallImageMapper;
    CinemaGrpcClient cinemaGrpcClient;
    ShowtimeGrpcClient showtimeGrpcClient;
    BookingGrpcClient bookingGrpcClient;
    SeatGrpcClient seatGrpcClient;

    @Override
    @Transactional
    public ActionMessageResponse createHall(HallCreateRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(cinemaId, request.getName())) {
            throw new BusinessException(ErrorCode.HALL_NAME_EXISTED);
        }

        validateSeats(request.getSeats());

        Hall hall = hallMapper.toEntity(request);
        hall.setCinemaId(cinemaId);
        hall.setStatus(request.getStatus() == null ? HallEnum.HallStatus.ACTIVE : request.getStatus());
        hallRepository.save(hall);

        List<Seat> seats = buildSeatEntities(hall, request.getSeats());
        seatRepository.saveAll(seats);

        return ActionMessageResponse.builder()
                .message("Tạo phòng chiếu thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_HALLS, key = "#id")
    public HallResponse getHallById(UUID id) {
        return toHallResponse(getActiveHallOrThrow(id), new HashMap<>());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HallResponse> searchHalls(PageRequest<HallField> request) {
        String keyword = request.getNormalizedKeyword();
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();

        List<SortField<HallField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        boolean hasIdSort = sortFields.stream()
                .anyMatch(sort -> sort != null && sort.getField() == HallField.ID);
        if (!hasIdSort) {
            sortFields.add(new SortField<>(HallField.ID, "ASC"));
        }

        List<FilterField<HallField>> filterFields = request.getFilterBy();
        long totalElements = hallRepositoryImpl.countWithFilter(keyword, filterFields);
        List<Hall> halls = hallRepositoryImpl.searchWithPageAndSortAndFilter(
                keyword, page, size, sortFields, filterFields);

        Map<UUID, CinemaResponse> cinemaResponseCache = new HashMap<>();
        List<HallResponse> data = halls.stream()
                .map(hall -> toHallResponse(hall, cinemaResponseCache))
                .toList();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return PageResponse.<HallResponse>builder()
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
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse updateHall(UUID hallId, UpdateHallRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);

        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(
                hall.getCinemaId(), request.getName(), hallId)) {
            throw new BusinessException(ErrorCode.HALL_NAME_EXISTED);
        }

        hallMapper.updateEntityFromRequest(hall, request);
        hallRepository.save(hall);
        return ActionMessageResponse.builder()
                .message("Cập nhật phòng chiếu thành công")
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse updateHallStatus(UUID hallId, UpdateHallStatusRequest request,
                                                  HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);
        hall.setStatus(request.getStatus());
        hallRepository.save(hall);
        return ActionMessageResponse.builder()
                .message("Cập nhật trạng thái phòng chiếu thành công")
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse replaceHallSeats(UUID hallId, ReplaceHallSeatsRequest request,
                                                  HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);
        if (hall.getStatus() == HallEnum.HallStatus.MAINTENANCE) {
            throw new BusinessException(ErrorCode.HALL_MAINTENANCE);
        }

        validateSeats(request.getSeats());
        seatRepository.deleteAllByHall_Id(hallId);
        seatRepository.saveAll(buildSeatEntities(hall, request.getSeats()));

        return ActionMessageResponse.builder()
                .message("Cập nhật sơ đồ ghế thành công")
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse createHallLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request,
                                                            HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        getManagedHallOrThrow(hallId, cinemaId);
        return seatGrpcClient.createLayoutDefinition(hallId, request);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse replaceHallLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request,
                                                             HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        getManagedHallOrThrow(hallId, cinemaId);
        if (hallHasActiveBookingOnActiveShowtime(hallId)) {
            throw new BusinessException(ErrorCode.HALL_LAYOUT_IN_USE);
        }
        return seatGrpcClient.replaceLayoutDefinition(hallId, request);
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse deleteHall(UUID hallId, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);
        hall.setIsDeleted(true);
        hallRepository.save(hall);
        return ActionMessageResponse.builder()
                .message("Xóa phòng chiếu thành công")
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse addHallImage(UUID hallId, AddHallImageRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);

        String imagePath = normalizeAndValidateImagePath(request.getImagePath());
        if (hallImageRepository.existsByHall_IdAndImagePathAndIsDeletedFalse(hallId, imagePath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        HallImage hallImage = new HallImage();
        hallImage.setHall(hall);
        hallImage.setImagePath(imagePath);
        hallImageRepository.save(hallImage);

        return ActionMessageResponse.builder()
                .message("Thêm ảnh phòng chiếu thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HallImageResponse> getHallImages(UUID hallId) {
        getActiveHallOrThrow(hallId);
        return hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(hallId)
                .stream()
                .map(hallImageMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse deleteHallImage(UUID hallId, UUID imageId, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        getManagedHallOrThrow(hallId, cinemaId);

        HallImage hallImage = hallImageRepository.findByIdAndHall_IdAndIsDeletedFalse(imageId, hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        hallImage.setIsDeleted(true);
        hallImageRepository.save(hallImage);

        return ActionMessageResponse.builder()
                .message("Xóa ảnh phòng chiếu thành công")
                .build();
    }

    private Hall getActiveHallOrThrow(UUID hallId) {
        return hallRepository.findByIdAndIsDeletedFalse(hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HALL_NOT_FOUND));
    }

    private Hall getManagedHallOrThrow(UUID hallId, UUID cinemaId) {
        Hall hall = getActiveHallOrThrow(hallId);
        if (!cinemaId.equals(hall.getCinemaId())) {
            throw new BusinessException(ErrorCode.HALL_NOT_IN_CINEMA);
        }
        return hall;
    }

    private HallResponse toHallResponse(Hall hall, Map<UUID, CinemaResponse> cinemaResponseCache) {
        HallResponse response = hallMapper.toResponse(hall);
        response.setSeats(seatRepository.findAllByHall_IdAndIsDeletedFalseOrderByRowAscColAsc(hall.getId())
                .stream()
                .map(seatMapper::toResponse)
                .toList());
        response.setImages(hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(hall.getId())
                .stream()
                .map(hallImageMapper::toResponse)
                .toList());
        response.setCinemaResponse(cinemaResponseCache.computeIfAbsent(hall.getCinemaId(), this::resolveCinemaResponse));
        return response;
    }

    private CinemaResponse resolveCinemaResponse(UUID cinemaId) {
        try {
            String cinemaName = cinemaGrpcClient.getCinemaNameById(cinemaId);
            return CinemaResponse.builder()
                    .id(cinemaId)
                    .name(cinemaName)
                    .build();
        } catch (BusinessException ex) {
            log.warn("Cannot enrich cinema payload for hall response. cinemaId={}, errorCode={}",
                    cinemaId, ex.getErrorCode());
            return CinemaResponse.builder()
                    .id(cinemaId)
                    .build();
        }
    }

    private List<Seat> buildSeatEntities(Hall hall, List<SeatUpsertRequest> seatRequests) {
        List<Seat> seats = new ArrayList<>(seatRequests.size());
        for (SeatUpsertRequest request : seatRequests) {
            Seat seat = seatMapper.toEntity(request);
            seat.setHall(hall);
            seat.setSeatCode(normalizeSeatCode(request.getSeatCode()));
            seats.add(seat);
        }
        return seats;
    }

    private void validateSeats(List<SeatUpsertRequest> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Set<String> seatCodes = new LinkedHashSet<>();
        Set<String> cells = new HashSet<>();
        for (SeatUpsertRequest seat : seats) {
            String seatCode = normalizeSeatCode(seat.getSeatCode());
            if (!seatCodes.add(seatCode)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (seat.getSeatType() == HallEnum.SeatType.AISLE) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            String key = seat.getRow() + ":" + seat.getCol();
            if (!cells.add(key)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    private String normalizeAndValidateImagePath(String rawPath) {
        String imagePath = rawPath == null ? "" : rawPath.trim();
        if (imagePath.isEmpty() || imagePath.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!imagePath.startsWith("/") || imagePath.startsWith("//")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String lower = imagePath.toLowerCase(Locale.ROOT);
        if (lower.contains("://") || lower.startsWith("http:") || lower.startsWith("https:")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return imagePath;
    }

    private String normalizeSeatCode(String seatCode) {
        return seatCode == null ? "" : seatCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hallHasActiveBookingOnActiveShowtime(UUID hallId) {
        List<UUID> activeShowtimeIds = showtimeGrpcClient.listActiveShowtimeIdsByHall(hallId);
        if (activeShowtimeIds.isEmpty()) {
            return false;
        }
        return bookingGrpcClient.hasActiveBookingByShowtimeIds(activeShowtimeIds);
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER);
    }

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            return cinemaGrpcClient.getCinemaIdByUserId(userId);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.CINEMA_NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            throw ex;
        }
    }
}
