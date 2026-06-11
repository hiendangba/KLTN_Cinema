package com.cinema.hall_service.services.impl;

import com.cinema.Enum.HallEnum;
import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.hall_service.config.RedisConfig;
import com.cinema.hall_service.dto.request.CreateHallRequest;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    CacheManager cacheManager;

    @Override
    @Transactional
    public ActionMessageResponse createHall(CreateHallRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        validateCinemaOwnership(httpRequest, request.getCinemaId());
        UUID cinemaId = request.getCinemaId();
        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(cinemaId, request.getName())) {
            throw new BusinessException(ErrorCode.HALL_NAME_EXISTED);
        }

        Hall hall = hallMapper.toEntity(request);
        hall.setCinemaId(cinemaId);
        hall.setStatus(request.getStatus() == null ? HallEnum.HallStatus.ACTIVE : request.getStatus());
        hallRepository.save(hall);
        reconcileHallImages(hall, request.getImagePaths());

        // Vẫn cần Saga để đảm bảo consistency giữa Hall và LayoutDefinition,
        // nhưng tạm thời cứ tạo LayoutDefinition trước để tránh lỗi khi gọi
        // Seat Service trong quá trình tạo Hall
        seatGrpcClient.createLayoutDefinition(hall.getId(), request.getLayoutDefinition());

        return ActionMessageResponse.builder()
                .message(SuccessMessage.HALL_CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HallResponse getHallById(UUID id) {
        Hall hall = getActiveHallOrThrow(id);
        authorizeHallReadAccess(hall);
        return resolveHallResponse(hall, new HashMap<>());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<HallResponse> searchHalls(PageRequest<HallField> request) {
        HttpServletRequest currentRequest = getCurrentHttpRequest();
        if (currentRequest != null) {
            RequestAuthUtils.requireAnyRole(currentRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
        }

        PageRequest<HallField> scopedRequest = scopeHallSearchRequest(request, currentRequest);

        if (scopedRequest == null) {
            return emptyHallPageResponse(request);
        }

        String keyword = scopedRequest.getNormalizedKeyword();
        int page = scopedRequest.getPageOrDefault();
        int size = scopedRequest.getSizeOrDefault();

        List<SortField<HallField>> sortFields = scopedRequest.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        boolean hasIdSort = sortFields.stream()
                .anyMatch(sort -> sort != null && sort.getField() == HallField.ID);
        if (!hasIdSort) {
            sortFields.add(new SortField<>(HallField.ID, "ASC"));
        }

        List<FilterField<HallField>> filterFields = scopedRequest.getFilterBy();
        long totalElements = hallRepositoryImpl.countWithFilter(keyword, filterFields);
        List<Hall> halls = hallRepositoryImpl.searchWithPageAndSortAndFilter(
                keyword, page, size, sortFields, filterFields);

        Map<UUID, CinemaResponse> cinemaResponseCache = new HashMap<>();
        List<HallResponse> data = halls.stream()
                .map(hall -> resolveHallResponse(hall, cinemaResponseCache))
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
        validateCinemaOwnership(httpRequest, request.getCinemaId());
        Hall hall = getActiveHallOrThrow(hallId);

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
        reconcileHallImages(hall, request.getImagePaths());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.HALL_UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> listActiveHallIdsByCinema(UUID cinemaId) {
        return hallRepository.findAllByCinemaIdAndIsDeletedFalse(cinemaId).stream()
                .map(Hall::getId)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_HALLS, key = "#hallId")
    public ActionMessageResponse deleteHall(UUID hallId, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        Hall hall = getActiveHallOrThrow(hallId);
        validateCinemaOwnership(httpRequest, hall.getCinemaId());
        if (hallHasActiveBookingOnActiveShowtime(hallId)) {
            throw new BusinessException(ErrorCode.HALL_LAYOUT_IN_USE);
        }
        hall.setIsDeleted(true);
        hallRepository.save(hall);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.HALL_DELETED.getMessage())
                .build();
    }

    private Hall getActiveHallOrThrow(UUID hallId) {
        return hallRepository.findByIdAndIsDeletedFalse(hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HALL_NOT_FOUND));
    }

    private HallResponse toHallResponse(Hall hall, Map<UUID, CinemaResponse> cinemaResponseCache) {
        HallResponse response = hallMapper.toResponse(hall);
        response.setSeats(seatGrpcClient.getHallSeats(hall.getId()));
        response.setImages(hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(hall.getId())
                .stream()
                .map(hallImageMapper::toResponse)
                .toList());
        response.setCinemaResponse(
                cinemaResponseCache.computeIfAbsent(hall.getCinemaId(), this::resolveCinemaResponse));
        return response;
    }

    private HallResponse resolveHallResponse(Hall hall, Map<UUID, CinemaResponse> cinemaResponseCache) {
        Cache cache = cacheManager.getCache(RedisConfig.CACHE_HALLS);
        if (cache != null) {
            HallResponse cached = cache.get(hall.getId(), HallResponse.class);
            if (cached != null) {
                return cached;
            }
        }

        HallResponse response = toHallResponse(hall, cinemaResponseCache);
        if (cache != null) {
            cache.put(hall.getId(), response);
        }
        return response;
    }

    private void reconcileHallImages(Hall hall, List<String> rawImagePaths) {
        List<String> imagePaths = rawImagePaths == null ? List.of() : rawImagePaths;
        Set<String> normalizedRequested = normalizeAndValidateRequestedImagePaths(imagePaths);
        List<HallImage> existingImages = hallImageRepository.findAllByHall_Id(hall.getId());
        Map<String, HallImage> existingByPath = indexHallImagesByPath(existingImages);
        validateNoDuplicateWithOtherHall(normalizedRequested, existingByPath, hall.getId());
        List<HallImage> imagesToSave = new ArrayList<>();
        collectImagesToUpsert(hall, normalizedRequested, existingByPath, imagesToSave);
        collectImagesToSoftDelete(normalizedRequested, existingImages, imagesToSave);
        if (!imagesToSave.isEmpty()) {
            hallImageRepository.saveAll(imagesToSave);
        }
    }

    private Set<String> normalizeAndValidateRequestedImagePaths(List<String> imagePaths) {
        Set<String> normalizedRequested = new LinkedHashSet<>();
        for (String rawPath : imagePaths) {
            String normalized = normalizeAndValidateImagePath(rawPath);
            if (!normalizedRequested.add(normalized)) {
                throw new BusinessException(ErrorCode.HALL_IMAGE_URL_ALREADY_EXISTS);
            }
        }
        return normalizedRequested;
    }

    private Map<String, HallImage> indexHallImagesByPath(List<HallImage> existingImages) {
        Map<String, HallImage> existingByPath = new HashMap<>();
        for (HallImage image : existingImages) {
            existingByPath.put(image.getImagePath(), image);
        }
        return existingByPath;
    }

    private void validateNoDuplicateWithOtherHall(
            Set<String> normalizedRequested,
            Map<String, HallImage> existingByPath,
            UUID hallId) {
        for (String path : normalizedRequested) {
            if (!existingByPath.containsKey(path)
                    && hallImageRepository.existsByImagePathAndHall_IdNot(path, hallId)) {
                throw new BusinessException(ErrorCode.HALL_IMAGE_URL_ALREADY_EXISTS);
            }
        }
    }

    private void collectImagesToUpsert(
            Hall hall,
            Set<String> normalizedRequested,
            Map<String, HallImage> existingByPath,
            List<HallImage> imagesToSave) {
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
    }

    private void collectImagesToSoftDelete(
            Set<String> normalizedRequested,
            List<HallImage> existingImages,
            List<HallImage> imagesToSave) {
        for (HallImage existing : existingImages) {
            if (!normalizedRequested.contains(existing.getImagePath())
                    && !Boolean.TRUE.equals(existing.getIsDeleted())) {
                existing.setIsDeleted(true);
                imagesToSave.add(existing);
            }
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

    private void validateCinemaOwnership(HttpServletRequest httpRequest, UUID cinemaId) {
        if (cinemaId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            List<UUID> managedCinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId);
            if (managedCinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            if (!managedCinemaIds.contains(cinemaId)) {
                throw new BusinessException(ErrorCode.HALL_NOT_IN_CINEMA);
            }
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.CINEMA_NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            throw ex;
        }
    }

    private PageRequest<HallField> scopeHallSearchRequest(PageRequest<HallField> request, HttpServletRequest currentRequest) {
        if (currentRequest == null) {
            return request;
        }

        String role = RequestAuthUtils.requireRoleHeader(currentRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return request;
        }
        if (!HeaderNames.ROLE_MANAGER.equals(role) && !HeaderNames.ROLE_STAFF.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        UUID userId = RequestAuthUtils.requireUserId(currentRequest);
        List<UUID> managedCinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
        if (managedCinemaIds.isEmpty()) {
            return null;
        }

        List<FilterField<HallField>> filterFields = request.getFilterBy() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getFilterBy());
        filterFields.add(FilterField.<HallField>builder()
                .field(HallField.CINEMA_ID)
                .operator("IN")
                .value(managedCinemaIds)
                .build());

        return PageRequest.<HallField>builder()
                .page(request.getPage())
                .size(request.getSize())
                .keyword(request.getKeyword())
                .sortBy(request.getSortBy() == null ? null : new ArrayList<>(request.getSortBy()))
                .filterBy(filterFields)
                .build();
    }

    private PageResponse<HallResponse> emptyHallPageResponse(PageRequest<HallField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        return PageResponse.<HallResponse>builder()
                .data(List.of())
                .currentPage(page)
                .totalPages(0)
                .totalElements(0L)
                .size(size)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }

    private void authorizeHallReadAccess(Hall hall) {
        HttpServletRequest currentRequest = getCurrentHttpRequest();
        if (currentRequest == null) {
            return;
        }

        String role = RequestAuthUtils.requireRoleHeader(currentRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (!HeaderNames.ROLE_MANAGER.equals(role) && !HeaderNames.ROLE_STAFF.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        UUID userId = RequestAuthUtils.requireUserId(currentRequest);
        List<UUID> managedCinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
        if (managedCinemaIds.isEmpty()) {
            throw new BusinessException(HeaderNames.ROLE_STAFF.equals(role)
                    ? ErrorCode.FORBIDDEN
                    : ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
        }
        if (!managedCinemaIds.contains(hall.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private HttpServletRequest getCurrentHttpRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
