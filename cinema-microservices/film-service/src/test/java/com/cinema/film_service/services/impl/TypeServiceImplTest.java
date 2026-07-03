package com.cinema.film_service.services.impl;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.PageResponse;
import com.cinema.film_service.dto.request.FilmTypeField;
import com.cinema.film_service.entity.FilmType;
import com.cinema.film_service.repository.FilmTypeRepository;
import com.cinema.film_service.services.FilmCatalogSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypeServiceImplTest {

    @Mock
    private FilmTypeRepository filmTypeRepository;

    @Mock
    private FilmCatalogSyncService filmCatalogSyncService;

    @InjectMocks
    private TypeServiceImpl typeService;

    @Test
    void searchTypes_shouldFilterAndPaginate() {
        LocalDateTime now = LocalDateTime.now();
        FilmType action = buildType("Action", now.minusDays(2));
        FilmType drama = buildType("Drama", now.minusDays(1));
        FilmType comedy = buildType("Comedy", now);
        when(filmTypeRepository.findAllByIsDeletedFalseOrderByNameAsc())
                .thenReturn(List.of(action, comedy, drama));

        PageRequest<FilmTypeField> request = PageRequest.<FilmTypeField>builder()
                .page(1)
                .size(2)
                .keyword("a")
                .build();

        PageResponse<?> response = typeService.searchTypes(request);

        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getCurrentPage()).isEqualTo(1);
        assertThat(response.getData()).hasSize(2);
        assertThat(((List<?>) response.getData()).get(0)).extracting("name").isEqualTo("Action");
        assertThat(((List<?>) response.getData()).get(1)).extracting("name").isEqualTo("Drama");
    }

    private FilmType buildType(String name, LocalDateTime timeCreated) {
        FilmType type = new FilmType();
        type.setId(UUID.randomUUID());
        type.setName(name);
        type.setIsDeleted(false);
        type.setTimeCreated(timeCreated);
        type.setTimeUpdated(timeCreated);
        return type;
    }
}
