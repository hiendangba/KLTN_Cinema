package com.cinema.film_service.services.impl;

import com.cinema.Enum.FilmEnum;
import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.BatchFilmRequest;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmCursorPageRequest;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.BatchFilmResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.grpc.ShowtimeGrpcClient;
import com.cinema.film_service.mapper.FilmMapper;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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
    private final ShowtimeGrpcClient showtimeGrpcClient;

    @Override
    public ActionMessageResponse createFilm(CreateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Creating film: {}", request.getTitle());
        validateAdminRole(httpRequest, "createFilm");
        if (filmRepository.findByTitleAndReleaseDateAndIsDeletedFalse(request.getTitle(), request.getReleaseDate()).isPresent()) {
            log.error("Film '{}' released in {} already exists", request.getTitle(), request.getReleaseDate().getYear());
            throw new BusinessException(ErrorCode.FILM_TITLE_EXISTED);
        }

        Film film = filmMapper.toEntity(request);
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

        return filmMapper.toResponse(film);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchFilmResponse getFilmsInBatch(BatchFilmRequest request) {
        log.info("Getting films by batch: {} ids", request.getIds().size());
        List<Film> films = filmRepository.findAllById(request.getIds());
        List<FilmResponse> filmResponses = films.stream()
                .filter(film -> !film.getIsDeleted())
                .map(filmMapper::toResponse)
                .collect(Collectors.toList());
        return new BatchFilmResponse(filmResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<FilmResponse> searchFilms(FilmCursorPageRequest request) {
        return searchFilmsInternal(request, null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<FilmResponse> searchCustomerFilms(FilmCursorPageRequest request, HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);

        Set<UUID> activeFilmIds = request.getCinemaId() == null
                ? showtimeGrpcClient.getActiveFilmIds()
                : showtimeGrpcClient.getActiveFilmIdsByCinema(request.getCinemaId());
        if (activeFilmIds.isEmpty()) {
            return emptyCursorPageResponse();
        }

        return searchFilmsInternal(request, activeFilmIds);
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
            Set<UUID> activeFilmIds) {
        log.info(
                "Getting films (cursor={}, size={}, keyword={}, sortBy={}, filterBy={}, dateRange={}, cinemaId={}, customerScope={})",
                request.getCursor(),
                request.getSize(),
                request.getKeyword(),
                request.getSortBy(),
                request.getFilterBy(),
                request.getDateRange(),
                request.getCinemaId(),
                activeFilmIds != null);

        String[] cursorParts = request.getParsedCompositeCursor();
        String keyword = request.getNormalizedKeyword();
        int size = request.getSizeOrDefault();

        List<SortField<FilmField>> sortFields = buildSortFields(request.getSortBy());
        List<FilterField<FilmField>> filterFields = request.getFilterBy() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.getFilterBy());
        appendReleaseDateRangeFilter(filterFields, request.getDateRange());

        if (activeFilmIds != null) {
            filterFields = scopeCustomerFilters(filterFields, activeFilmIds);
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
                .data(films.stream().map(filmMapper::toResponse).collect(Collectors.toList()))
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

    private CursorPageResponse<FilmResponse> emptyCursorPageResponse() {
        return CursorPageResponse.<FilmResponse>builder()
                .data(List.of())
                .nextCursor(null)
                .prevCursor(null)
                .hasNext(false)
                .size(0)
                .build();
    }

    private void validateAdminRole(HttpServletRequest httpRequest, String action) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, action);
    }

    private void validateCustomerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_CUSTOMER, log, "searchCustomerFilms");
    }
}
