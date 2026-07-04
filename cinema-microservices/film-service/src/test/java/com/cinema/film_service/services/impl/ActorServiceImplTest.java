package com.cinema.film_service.services.impl;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.PageResponse;
import com.cinema.film_service.dto.request.ActorField;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.repository.ActorRepository;
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
class ActorServiceImplTest {

    @Mock
    private ActorRepository actorRepository;

    @InjectMocks
    private ActorServiceImpl actorService;

    @Test
    void searchActors_shouldSortByNameAndPaginate() {
        LocalDateTime now = LocalDateTime.now();
        Actor bob = buildActor("Bob", 1990, now.minusDays(2));
        Actor alice = buildActor("Alice", 1988, now.minusDays(1));
        Actor charlie = buildActor("Charlie", 1992, now);
        when(actorRepository.findAllByIsDeletedFalseOrderByNameAsc())
                .thenReturn(List.of(alice, bob, charlie));

        PageRequest<ActorField> request = PageRequest.<ActorField>builder()
                .page(2)
                .size(1)
                .build();

        PageResponse<?> response = actorService.searchActors(request);

        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getCurrentPage()).isEqualTo(2);
        assertThat(response.getData()).hasSize(1);
        assertThat(((List<?>) response.getData()).get(0)).extracting("name").isEqualTo("Bob");
    }

    private Actor buildActor(String name, Integer birthYear, LocalDateTime timeCreated) {
        Actor actor = new Actor();
        actor.setId(UUID.randomUUID());
        actor.setName(name);
        actor.setBirthYear(birthYear);
        actor.setHometown("Ha Noi");
        actor.setAvatarUrl("https://example.com/avatar.png");
        actor.setIsDeleted(false);
        actor.setTimeCreated(timeCreated);
        actor.setTimeUpdated(timeCreated);
        return actor;
    }
}
