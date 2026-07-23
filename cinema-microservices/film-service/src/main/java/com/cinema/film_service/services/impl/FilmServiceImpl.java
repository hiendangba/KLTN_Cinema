package com.cinema.film_service.services.impl;

import com.cinema.Enum.FilmEnum;
import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.BatchFilmRequest;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmCursorPageRequest;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.BatchFilmResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.entity.FilmType;
import com.cinema.film_service.grpc.CinemaGrpcClient;
import com.cinema.film_service.grpc.ReviewGrpcClient;
import com.cinema.film_service.grpc.ShowtimeGrpcClient;
import com.cinema.film_service.mapper.FilmMapper;
import com.cinema.film_service.repository.ActorRepository;
import com.cinema.film_service.repository.FilmTypeRepository;
import com.cinema.film_service.repository.FilmRepository;
import com.cinema.film_service.repository.FilmRepositoryImpl;
import com.cinema.film_service.services.FilmService;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class FilmServiceImpl implements FilmService {

    private final FilmRepository filmRepository;
    private final FilmRepositoryImpl filmRepositoryImpl;
    private final FilmMapper filmMapper;
    private final ReviewGrpcClient reviewGrpcClient;
    private final FilmTypeRepository filmTypeRepository;
    private final ActorRepository actorRepository;
    private final ShowtimeGrpcClient showtimeGrpcClient;
    private final CinemaGrpcClient cinemaGrpcClient;

    @Override
    public ActionMessageResponse createFilm(CreateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Creating film: {}", request.getTitle());
        validateAdminRole(httpRequest, "createFilm");
        if (filmRepository.findByTitleAndReleaseDateAndIsDeletedFalse(request.getTitle(), request.getReleaseDate()).isPresent()) {
            log.error("Film '{}' released in {} already exists", request.getTitle(), request.getReleaseDate().getYear());
            throw new BusinessException(ErrorCode.FILM_TITLE_EXISTED);
        }

        Film film = filmMapper.toEntity(request);
        film.setTypes(resolveTypes(request.getTypeIds()));
        film.setActors(resolveActors(request.getActorIds()));
        Film savedFilm = filmRepository.save(film);
        log.info("Film created successfully with ID: {}", savedFilm.getId());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.FILM_CREATED.getMessage())
                .build();
    }

    @Override
    @CacheEvict(value = "films", key = "#id")
    public ActionMessageResponse updateFilm(UUID id, UpdateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Updating film with ID: {}", id);
        validateAdminRole(httpRequest, "updateFilm");
        Film film = filmRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Film not found with ID: {}", id);
                    return new BusinessException(ErrorCode.FILM_NOT_FOUND);
                });

        filmMapper.updateEntityFromRequest(film, request);
        film.setTypes(resolveTypes(request.getTypeIds()));
        film.setActors(resolveActors(request.getActorIds()));

        if (filmRepository.existsByTitleAndReleaseDateAndIdNotAndIsDeletedFalse(
                request.getTitle(), request.getReleaseDate(), id)) {
            log.error("Film '{}' released in {} already exists", request.getTitle(), request.getReleaseDate().getYear());
            throw new BusinessException(ErrorCode.FILM_TITLE_EXISTED);
        }

        filmRepository.save(film);
        log.info("Film updated successfully: {}", id);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.FILM_UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "films", key = "#id")
    public FilmResponse getFilmById(UUID id) {
        log.info("Getting film by ID: {}", id);
        Film film = filmRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> {
                    log.error("Film not found with ID: {}", id);
                    return new BusinessException(ErrorCode.FILM_NOT_FOUND);
                });

        return enrichRating(filmMapper.toResponse(film), reviewGrpcClient.getFilmRatingSummaries(List.of(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchFilmResponse getFilmsInBatch(BatchFilmRequest request) {
        log.info("Getting films by batch: {} ids", request.getIds().size());
        List<Film> films = filmRepository.findAllById(request.getIds());
        List<FilmResponse> filmResponses = enrichResponses(films.stream()
                .filter(film -> !film.getIsDeleted())
                .toList());
        return new BatchFilmResponse(filmResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<FilmResponse> searchFilms(FilmCursorPageRequest request) {
        Set<UUID> showtimeScopedFilmIds = resolveActiveFilmIdsByShowtimeDate(request == null ? null : request.getShowtimeDate());
        if (request != null && request.getShowtimeDate() != null && showtimeScopedFilmIds.isEmpty()) {
            return emptyCursorPageResponse();
        }
        return searchFilmsInternal(request, showtimeScopedFilmIds, false);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<FilmResponse> searchCustomerFilms(FilmCursorPageRequest request, HttpServletRequest httpRequest) {
        String role = normalizeRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        Set<UUID> scopedFilmIds = resolveScopedActiveFilmIds(request, httpRequest, role);
        if (scopedFilmIds.isEmpty()) {
            return emptyCursorPageResponse();
        }

        Set<UUID> showtimeScopedFilmIds = resolveActiveFilmIdsByShowtimeDate(request == null ? null : request.getShowtimeDate());
        if (request != null && request.getShowtimeDate() != null) {
            scopedFilmIds = intersectFilmIds(scopedFilmIds, showtimeScopedFilmIds);
            if (scopedFilmIds.isEmpty()) {
                return emptyCursorPageResponse();
            }
        }

        return searchFilmsInternal(request, scopedFilmIds, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<String> searchDirectors(PageRequest<FilmField> request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        String keyword = request.getNormalizedKeyword();
        Page<String> directorsPage = filmRepositoryImpl.searchDistinctDirectors(
                keyword,
                request.toPageable());

        return PageResponse.<String>builder()
                .data(directorsPage.getContent())
                .currentPage(directorsPage.getNumber() + 1)
                .totalPages(directorsPage.getTotalPages())
                .totalElements(directorsPage.getTotalElements())
                .size(directorsPage.getSize())
                .hasNext(directorsPage.hasNext())
                .hasPrevious(directorsPage.hasPrevious())
                .build();
    }

    @Override
    @CacheEvict(value = "films", key = "#id")
    public ActionMessageResponse deleteFilm(UUID id, HttpServletRequest httpRequest) {
        log.info("Deleting film with ID: {}", id);
        validateAdminRole(httpRequest, "deleteFilm");
        Film film = filmRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> {
                    log.error("Film not found with ID: {}", id);
                    return new BusinessException(ErrorCode.FILM_NOT_FOUND);
                });

        film.setIsDeleted(true);
        filmRepository.save(film);
        log.info("Film deleted successfully: {}", id);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.FILM_DELETED.getMessage())
                .build();
    }

    private CursorPageResponse<FilmResponse> searchFilmsInternal(
            FilmCursorPageRequest request,
            Set<UUID> scopedFilmIds,
            boolean applyCustomerStatusScope) {
        log.info(
                "Getting films (cursor={}, size={}, keyword={}, sortBy={}, filterBy={}, dateRange={}, showtimeDate={}, cinemaId={}, customerScope={})",
                request.getCursor(),
                request.getSize(),
                request.getKeyword(),
                request.getSortBy(),
                request.getFilterBy(),
                request.getDateRange(),
                request.getShowtimeDate(),
                request.getCinemaId(),
                applyCustomerStatusScope);

        String[] cursorParts = request.getParsedCompositeCursor();
        String keyword = request.getNormalizedKeyword();
        int size = request.getSizeOrDefault();

        List<SortField<FilmField>> sortFields = buildSortFields(request.getSortBy());
        List<FilterField<FilmField>> filterFields = request.getFilterBy() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getFilterBy());
        appendReleaseDateRangeFilter(filterFields, request.getDateRange());
        appendGenreFilter(filterFields, request == null ? null : request.getGenre());

        if (scopedFilmIds != null) {
            if (applyCustomerStatusScope) {
                filterFields = scopeCustomerFilters(filterFields, scopedFilmIds);
            } else {
                filterFields = scopeFilmFilters(filterFields, scopedFilmIds);
            }
        }

        List<Film> films = filmRepositoryImpl.searchWithCursorAndSortAndFilter(
                cursorParts, keyword, size, sortFields, filterFields);

        boolean hasNext = films.size() > size;
        String nextCursor = null;
        if (hasNext) {
            films = films.subList(0, size);
            nextCursor = CursorPageRequest.encodeCompositeCursor(
                    FilmField.getFieldValues(films.get(films.size() - 1), sortFields));
        }

        String prevCursor = null;
        if (cursorParts != null && cursorParts.length > 0) {
            List<Film> prevFilms = filmRepositoryImpl.previousCursor(
                    cursorParts, keyword, size, sortFields, filterFields);
            if (!prevFilms.isEmpty() && prevFilms.size() == size) {
                prevCursor = CursorPageRequest.encodeCompositeCursor(
                        FilmField.getFieldValues(prevFilms.get(size - 1), sortFields));
            }
        }

        return CursorPageResponse.<FilmResponse>builder()
                .data(enrichResponses(films))
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasNext(hasNext)
                .size(films.size())
                .build();
    }

    private List<SortField<FilmField>> buildSortFields(List<SortField<FilmField>> requestedSortFields) {
        List<SortField<FilmField>> sortFields = requestedSortFields == null
                ? new ArrayList<>()
                : new ArrayList<>(requestedSortFields);

        boolean hasExplicitSort = !sortFields.isEmpty();
        sortFields.removeIf(sortField -> sortField != null && sortField.getField() == FilmField.ID);

        if (!hasExplicitSort) {
            sortFields.add(0, new SortField<>(FilmField.RELEASE_DATE, "DESC"));
        }

        sortFields.add(new SortField<>(FilmField.ID, "ASC"));
        return sortFields;
    }

    private void appendReleaseDateRangeFilter(List<FilterField<FilmField>> filterFields, DateRange dateRange) {
        if (dateRange == null) {
            return;
        }

        if (dateRange.getFrom() != null && dateRange.getTo() != null) {
            filterFields.add(FilterField.<FilmField>builder()
                    .field(FilmField.RELEASE_DATE)
                    .operator("BETWEEN")
                    .value(List.of(dateRange.getFrom().toLocalDate(), dateRange.getTo().toLocalDate()))
                    .build());
            return;
        }

        if (dateRange.getFrom() != null) {
            filterFields.add(FilterField.<FilmField>builder()
                    .field(FilmField.RELEASE_DATE)
                    .operator("GTE")
                    .value(dateRange.getFrom().toLocalDate())
                    .build());
        }

        if (dateRange.getTo() != null) {
            filterFields.add(FilterField.<FilmField>builder()
                    .field(FilmField.RELEASE_DATE)
                    .operator("LTE")
                    .value(dateRange.getTo().toLocalDate())
                    .build());
        }
    }

    private void appendGenreFilter(List<FilterField<FilmField>> filterFields, String genre) {
        if (!org.springframework.util.StringUtils.hasText(genre)) {
            return;
        }

        filterFields.add(FilterField.<FilmField>builder()
                .field(FilmField.TYPE)
                .operator("LIKE")
                .value(genre.trim())
                .build());
    }

    private List<FilterField<FilmField>> scopeCustomerFilters(
            List<FilterField<FilmField>> requestedFilters,
            Set<UUID> activeFilmIds) {
        List<FilterField<FilmField>> scopedFilters = requestedFilters.stream()
                .filter(filter -> filter != null && filter.getField() != FilmField.STATUS)
                .collect(Collectors.toCollection(ArrayList::new));

        scopedFilters.add(FilterField.<FilmField>builder()
                .field(FilmField.STATUS)
                .operator("IN")
                .value(List.of(
                        FilmEnum.FilmStatus.NOW_SHOWING.name(),
                        FilmEnum.FilmStatus.COMING_SOON.name()))
                .build());

        scopedFilters.add(FilterField.<FilmField>builder()
                .field(FilmField.ID)
                .operator("IN")
                .value(activeFilmIds.stream().map(UUID::toString).toList())
                .build());
        return scopedFilters;
    }

    private List<FilterField<FilmField>> scopeFilmFilters(
            List<FilterField<FilmField>> requestedFilters,
            Set<UUID> scopedFilmIds) {
        List<FilterField<FilmField>> scopedFilters = new ArrayList<>(requestedFilters);
        scopedFilters.add(FilterField.<FilmField>builder()
                .field(FilmField.ID)
                .operator("IN")
                .value(scopedFilmIds.stream().map(UUID::toString).toList())
                .build());
        return scopedFilters;
    }

    private CursorPageResponse<FilmResponse> emptyCursorPageResponse() {
        return CursorPageResponse.<FilmResponse>builder()
                .data(List.of())
                .nextCursor(null)
                .prevCursor(null)
                .hasNext(false)
                .size(0)
                .build();
    }

    private Set<UUID> resolveScopedActiveFilmIds(FilmCursorPageRequest request, HttpServletRequest httpRequest, String role) {
        UUID requestedCinemaId = request == null ? null : request.getCinemaId();
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return requestedCinemaId == null
                    ? showtimeGrpcClient.getActiveFilmIds()
                    : showtimeGrpcClient.getActiveFilmIdsByCinema(requestedCinemaId);
        }

        if (HeaderNames.ROLE_MANAGER.equals(role) || HeaderNames.ROLE_STAFF.equals(role)) {
            UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest);
            List<UUID> accessibleCinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(requesterUserId, role);
            if (requestedCinemaId != null) {
                if (!accessibleCinemaIds.contains(requestedCinemaId)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN);
                }
                return showtimeGrpcClient.getActiveFilmIdsByCinema(requestedCinemaId);
            }

            if (accessibleCinemaIds.isEmpty()) {
                return Set.of();
            }

            Set<UUID> activeFilmIds = new LinkedHashSet<>();
            for (UUID cinemaId : accessibleCinemaIds) {
                activeFilmIds.addAll(showtimeGrpcClient.getActiveFilmIdsByCinema(cinemaId));
            }
            return activeFilmIds;
        }

        if (HeaderNames.ROLE_CUSTOMER.equals(role)) {
            return requestedCinemaId == null
                    ? showtimeGrpcClient.getActiveFilmIds()
                    : showtimeGrpcClient.getActiveFilmIdsByCinema(requestedCinemaId);
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private Set<UUID> resolveActiveFilmIdsByShowtimeDate(LocalDate showtimeDate) {
        if (showtimeDate == null) {
            return null;
        }
        return showtimeGrpcClient.getActiveFilmIdsByShowtimeDate(showtimeDate);
    }

    private Set<UUID> intersectFilmIds(Set<UUID> left, Set<UUID> right) {
        if (left == null || left.isEmpty()) {
            return left == null ? right : Set.of();
        }
        if (right == null || right.isEmpty()) {
            return Set.of();
        }
        Set<UUID> intersected = new LinkedHashSet<>(left);
        intersected.retainAll(right);
        return intersected;
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private void validateAdminRole(HttpServletRequest httpRequest, String action) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, action);
    }

    private Set<FilmType> resolveTypes(List<UUID> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<FilmType> types = filmTypeRepository.findAllByIdInAndIsDeletedFalse(typeIds);
        if (types.size() != new LinkedHashSet<>(typeIds).size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new LinkedHashSet<>(types);
    }

    private Set<Actor> resolveActors(List<UUID> actorIds) {
        if (actorIds == null || actorIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        List<Actor> actors = actorRepository.findAllByIdInAndIsDeletedFalse(actorIds);
        if (actors.size() != new LinkedHashSet<>(actorIds).size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new LinkedHashSet<>(actors);
    }

    private List<FilmResponse> enrichResponses(List<Film> films) {
        if (films == null || films.isEmpty()) {
            return List.of();
        }

        List<UUID> filmIds = films.stream()
                .map(Film::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<UUID, ReviewGrpcClient.RatingSummary> summaries = reviewGrpcClient.getFilmRatingSummaries(filmIds);
        if (summaries.isEmpty()) {
            log.warn("No review rating summaries returned for filmIds={}", filmIds);
        }

        return films.stream()
                .map(film -> enrichRating(filmMapper.toResponse(film), summaries))
                .collect(Collectors.toList());
    }

    private FilmResponse enrichRating(FilmResponse response, Map<UUID, ReviewGrpcClient.RatingSummary> summaries) {
        if (response == null) {
            return null;
        }

        ReviewGrpcClient.RatingSummary summary = summaries == null ? null : summaries.get(response.getId());
        if (summary == null) {
            response.setAverageRating(0.0d);
            response.setReviewCount(0L);
            return response;
        }

        response.setAverageRating(roundToTwoDecimals(summary.averageRating()));
        response.setReviewCount(summary.reviewCount());
        return response;
    }

    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
