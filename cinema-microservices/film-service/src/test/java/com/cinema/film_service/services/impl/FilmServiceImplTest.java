package com.cinema.film_service.services.impl;

import com.cinema.Enum.FilmEnum;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.FilmCursorPageRequest;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.grpc.ShowtimeGrpcClient;
import com.cinema.film_service.mapper.FilmMapper;
import com.cinema.film_service.repository.FilmRepository;
import com.cinema.film_service.repository.FilmRepositoryImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilmServiceImplTest {

    @Mock
    private FilmRepository filmRepository;

    @Mock
    private FilmRepositoryImpl filmRepositoryImpl;

    @Mock
    private FilmMapper filmMapper;

    @Mock
    private ShowtimeGrpcClient showtimeGrpcClient;

    @InjectMocks
    private FilmServiceImpl filmService;

    @Test
    void searchCustomerFilms_shouldRejectNonCustomer() {
        HttpServletRequest request = mockRequest("MANAGER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> filmService.searchCustomerFilms(new FilmCursorPageRequest(), request));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(showtimeGrpcClient, never()).getActiveFilmIds();
    }

    @Test
    void searchCustomerFilms_shouldScopeToAllowedStatusesAndActiveFilmIds() {
        UUID film1Id = UUID.randomUUID();
        UUID film2Id = UUID.randomUUID();
        Film film1 = buildFilm(film1Id, "Film 1", FilmEnum.FilmStatus.NOW_SHOWING, LocalDate.of(2026, 5, 1));
        Film film2 = buildFilm(film2Id, "Film 2", FilmEnum.FilmStatus.COMING_SOON, LocalDate.of(2026, 6, 1));

        FilmCursorPageRequest request = new FilmCursorPageRequest();
        request.setSize(2);
        request.setFilterBy(List.of(
                FilterField.<FilmField>builder()
                        .field(FilmField.STATUS)
                        .operator("EQ")
                        .value(FilmEnum.FilmStatus.ENDED.name())
                        .build()));

        HttpServletRequest httpRequest = mockRequest("CUSTOMER");
        when(showtimeGrpcClient.getActiveFilmIds()).thenReturn(Set.of(film1Id, film2Id));
        when(filmRepositoryImpl.searchWithCursorAndSortAndFilter(any(), anyString(), anyInt(), anyList(), anyList()))
                .thenReturn(List.of(film1, film2));
        when(filmMapper.toResponse(film1)).thenReturn(toResponse(film1));
        when(filmMapper.toResponse(film2)).thenReturn(toResponse(film2));

        var response = filmService.searchCustomerFilms(request, httpRequest);

        assertEquals(2, response.getSize());
        assertEquals(2, response.getData().size());
        assertFalse(response.isHasNext());
        assertTrue(response.getNextCursor() == null);
        assertTrue(response.getPrevCursor() == null);

        ArgumentCaptor<List<FilterField<FilmField>>> filterCaptor = ArgumentCaptor.forClass(List.class);
        verify(filmRepositoryImpl).searchWithCursorAndSortAndFilter(any(), anyString(), anyInt(), anyList(), filterCaptor.capture());

        List<FilterField<FilmField>> capturedFilters = filterCaptor.getValue();
        assertEquals(2, capturedFilters.size());
        assertTrue(capturedFilters.stream().anyMatch(filter ->
                filter.getField() == FilmField.STATUS
                        && "IN".equals(filter.getOperator())
                        && new HashSet<>(List.of(
                        FilmEnum.FilmStatus.NOW_SHOWING.name(),
                        FilmEnum.FilmStatus.COMING_SOON.name()))
                        .equals(new HashSet<>((List<String>) filter.getValue()))));
        assertTrue(capturedFilters.stream().anyMatch(filter ->
                filter.getField() == FilmField.ID
                        && "IN".equals(filter.getOperator())
                        && new HashSet<>((List<String>) filter.getValue())
                        .equals(Set.of(film1Id.toString(), film2Id.toString()))));
    }

    @Test
    void searchCustomerFilms_shouldScopeByCinemaIdWhenProvided() {
        UUID cinemaId = UUID.randomUUID();
        UUID film1Id = UUID.randomUUID();
        Film film1 = buildFilm(film1Id, "Film 1", FilmEnum.FilmStatus.NOW_SHOWING, LocalDate.of(2026, 5, 1));

        FilmCursorPageRequest request = new FilmCursorPageRequest();
        request.setCinemaId(cinemaId);

        HttpServletRequest httpRequest = mockRequest("CUSTOMER");
        when(showtimeGrpcClient.getActiveFilmIdsByCinema(cinemaId)).thenReturn(Set.of(film1Id));
        when(filmRepositoryImpl.searchWithCursorAndSortAndFilter(any(), anyString(), anyInt(), anyList(), anyList()))
                .thenReturn(List.of(film1));
        when(filmMapper.toResponse(film1)).thenReturn(toResponse(film1));

        var response = filmService.searchCustomerFilms(request, httpRequest);

        assertEquals(1, response.getSize());
        assertEquals(1, response.getData().size());
        verify(showtimeGrpcClient, never()).getActiveFilmIds();
    }

    @Test
    void searchCustomerFilms_shouldKeepCursorPagination() {
        UUID film1Id = UUID.randomUUID();
        UUID film2Id = UUID.randomUUID();
        Film film1 = buildFilm(film1Id, "Film 1", FilmEnum.FilmStatus.NOW_SHOWING, LocalDate.of(2026, 5, 1));
        Film film2 = buildFilm(film2Id, "Film 2", FilmEnum.FilmStatus.NOW_SHOWING, LocalDate.of(2026, 5, 2));

        FilmCursorPageRequest request = new FilmCursorPageRequest();
        request.setSize(1);

        HttpServletRequest httpRequest = mockRequest("CUSTOMER");
        when(showtimeGrpcClient.getActiveFilmIds()).thenReturn(Set.of(film1Id, film2Id));
        when(filmRepositoryImpl.searchWithCursorAndSortAndFilter(any(), anyString(), anyInt(), anyList(), anyList()))
                .thenReturn(List.of(film1, film2));
        when(filmMapper.toResponse(film1)).thenReturn(toResponse(film1));

        var response = filmService.searchCustomerFilms(request, httpRequest);

        assertEquals(1, response.getSize());
        assertEquals(1, response.getData().size());
        assertTrue(response.isHasNext());
        assertNotNull(response.getNextCursor());
    }

    private HttpServletRequest mockRequest(String role) {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Role")).thenReturn(role);
        return request;
    }

    private Film buildFilm(UUID id, String title, FilmEnum.FilmStatus status, LocalDate releaseDate) {
        Film film = new Film();
        film.setId(id);
        film.setTitle(title);
        film.setReleaseDate(releaseDate);
        film.setDuration(120);
        film.setCountry("VN");
        film.setLanguage("vi");
        film.setAgeRating(FilmEnum.AgeRating.RATING_3);
        film.setStatus(status);
        film.setIsDeleted(false);
        return film;
    }

    private FilmResponse toResponse(Film film) {
        return FilmResponse.builder()
                .id(film.getId())
                .title(film.getTitle())
                .releaseDate(film.getReleaseDate())
                .duration(film.getDuration())
                .country(film.getCountry())
                .language(film.getLanguage())
                .ageRating(film.getAgeRating())
                .status(film.getStatus())
                .build();
    }
}
