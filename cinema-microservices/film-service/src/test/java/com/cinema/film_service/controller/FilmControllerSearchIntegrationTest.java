package com.cinema.film_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.services.FilmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FilmControllerSearchIntegrationTest {

    @Mock
    private FilmService filmService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FilmController(filmService)).build();
    }

    @Test
    void searchFilms_shouldReturnAverageRatingAndReviewCountInFinalResponse() throws Exception {
        UUID filmId = UUID.randomUUID();
        FilmResponse filmResponse = new FilmResponse();
        filmResponse.setId(filmId);
        filmResponse.setTitle("Inception");
        filmResponse.setAverageRating(4.67d);
        filmResponse.setReviewCount(12L);

        CursorPageResponse<FilmResponse> cursorPageResponse = new CursorPageResponse<>();
        cursorPageResponse.setData(List.of(filmResponse));
        cursorPageResponse.setNextCursor(null);
        cursorPageResponse.setPrevCursor(null);
        cursorPageResponse.setHasNext(false);
        cursorPageResponse.setSize(1);

        when(filmService.searchFilms(any()))
                .thenReturn(cursorPageResponse);

        mockMvc.perform(post("/api/films/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "size": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(SuccessMessage.FILMS_SEARCHED.getMessage()))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.data[0].id").value(filmId.toString()))
                .andExpect(jsonPath("$.data.data[0].averageRating").value(4.67))
                .andExpect(jsonPath("$.data.data[0].reviewCount").value(12));
    }

    @Test
    void searchDirectors_shouldReturnPagedDirectorNames() throws Exception {
        PageResponse<String> response = PageResponse.<String>builder()
                .data(List.of("Christopher Nolan", "Denis Villeneuve"))
                .currentPage(1)
                .totalPages(1)
                .totalElements(2)
                .size(10)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(filmService.searchDirectors(any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/films/directors/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "size": 10,
                                  "keyword": "nolan"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value(SuccessMessage.FILMS_SEARCHED.getMessage()))
                .andExpect(jsonPath("$.data.data[0]").value("Christopher Nolan"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }
}
