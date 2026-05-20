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
import com.cinema.hall_service.dto.request.HallCreateRequest;
import com.cinema.hall_service.dto.request.HallField;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.dto.response.CinemaResponse;
import com.cinema.hall_service.dto.response.HallResponse;
import com.cinema.hall_service.entity.Hall;
import com.cinema.hall_service.entity.HallImage;
import com.cinema.hall_service.grpc.BookingGrpcClient;
import com.cinema.hall_service.grpc.CinemaGrpcClient;
import com.cinema.hall_service.grpc.SeatGrpcClient;
import com.cinema.hall_service.grpc.ShowtimeGrpcClient;
import com.cinema.hall_service.mapper.HallImageMapper;
import com.cinema.hall_service.mapper.HallMapper;
import com.cinema.hall_service.repository.HallImageRepository;
import com.cinema.hall_service.repository.HallRepository;
import com.cinema.hall_service.repository.HallRepositoryImpl;
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
    HallImageRepository hallImageRepository;
    HallMapper hallMapper;
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

        Hall hall = hallMapper.toEntity(request);
        hall.setCinemaId(cinemaId);
        hall.setStatus(request.getStatus() == null ? HallEnum.HallStatus.ACTIVE : request.getStatus());
        hallRepository.save(hall);
        seatGrpcClient.createLayoutDefinition(hall.getId(), request.getLayoutDefinition());
        syncHallImages(hall, request.getImagePaths());

        return ActionMessageResponse.builder()
                .message("Hall created successfully")
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

        if (hallHasActiveBookingOnActiveShowtime(hallId)) {
            throw new BusinessException(ErrorCode.HALL_LAYOUT_IN_USE);
        }
        hallMapper.updateEntityFromRequest(hall, request);
        hallRepository.save(hall);
        try {
            seatGrpcClient.replaceLayoutDefinition(hallId, request.getLayoutDefinition());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.NOT_FOUND) {
                seatGrpcClient.createLayoutDefinition(hallId, request.getLayoutDefinition());
            } else {
                throw ex;
            }
        }
        syncHallImages(hall, request.getImagePaths());
        return ActionMessageResponse.builder()
                .message("Hall updated successfully")
                .build();
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
                .message("Hall deleted successfully")
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
        response.setSeats(seatGrpcClient.getHallSeats(hall.getId()));
        response.setImages(hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(hall.getId())
                .stream()
                .map(hallImageMapper::toResponse)
                .toList());
        response.setCinemaResponse(cinemaResponseCache.computeIfAbsent(hall.getCinemaId(), this::resolveCinemaResponse));
        return response;
    }

    private void syncHallImages(Hall hall, List<String> rawImagePaths) {
        List<String> imagePaths = rawImagePaths == null ? List.of() : rawImagePaths;
        Set<String> normalizedRequested = new LinkedHashSet<>();
        for (String rawPath : imagePaths) {
            String normalized = normalizeAndValidateImagePath(rawPath);
            if (!normalizedRequested.add(normalized)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }

        List<HallImage> existingImages = hallImageRepository.findAllByHall_Id(hall.getId());
        Map<String, HallImage> existingByPath = new HashMap<>();
        for (HallImage image : existingImages) {
            existingByPath.put(image.getImagePath(), image);
        }

        List<HallImage> imagesToSave = new ArrayList<>();
        for (String path : normalizedRequested) {
            HallImage image = existingByPath.get(path);
            if (image == null) {
                image = new HallImage();
                image.setHall(hall);
                image.setImagePath(path);
                image.setIsDeleted(false);
                imagesToSave.add(image);
                continue;
            }
            if (Boolean.TRUE.equals(image.getIsDeleted())) {
                image.setIsDeleted(false);
                imagesToSave.add(image);
            }
        }

        for (HallImage existing : existingImages) {
            if (!normalizedRequested.contains(existing.getImagePath()) && !Boolean.TRUE.equals(existing.getIsDeleted())) {
                existing.setIsDeleted(true);
                imagesToSave.add(existing);
            }
        }

        if (!imagesToSave.isEmpty()) {
            hallImageRepository.saveAll(imagesToSave);
        }
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
