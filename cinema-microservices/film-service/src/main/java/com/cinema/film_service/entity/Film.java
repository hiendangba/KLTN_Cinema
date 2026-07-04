package com.cinema.film_service.entity;

import com.cinema.Enum.FilmEnum;
import com.github.f4b6a3.uuid.UuidCreator;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "films", indexes = {
        @Index(name = "idx_title", columnList = "title"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_release_date", columnList = "release_date"),
        @Index(name = "idx_country", columnList = "country"),
        @Index(name = "idx_language", columnList = "language"),
        @Index(name = "idx_is_deleted", columnList = "is_deleted"),
        @Index(
                name = "uk_film_title_release_date_active",
                columnList = "title, release_date",
                unique = true,
                options = "where is_deleted = false"
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Film {
    @Id
    @Column(columnDefinition = "uuid")
    UUID id;

    @Column(nullable = false, length = 255)
    String title;

    @Column(length = 100)
    String director;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "film_types_link",
            joinColumns = @JoinColumn(name = "film_id"),
            inverseJoinColumns = @JoinColumn(name = "film_type_id"))
    Set<FilmType> types = new LinkedHashSet<>();

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "film_actors_link",
            joinColumns = @JoinColumn(name = "film_id"),
            inverseJoinColumns = @JoinColumn(name = "actor_id"))
    Set<Actor> actors = new LinkedHashSet<>();

    @Column(name = "release_date", nullable = false)
    LocalDate releaseDate;

    @Column(columnDefinition = "text")
    String description;

    @Column(nullable = false)
    Integer duration;

    @Column(columnDefinition = "text")
    String poster;

    @Column(columnDefinition = "text")
    String trailer;

    @Column(nullable = false, length = 50)
    String country;

    @Column(nullable = false, length = 50)
    String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    FilmEnum.AgeRating ageRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    FilmEnum.FilmStatus status;

    @Column(name = "is_deleted", nullable = false)
    Boolean isDeleted;

    @Column(nullable = false, updatable = false)
    LocalDateTime timeCreated;

    @Column(nullable = false)
    LocalDateTime timeUpdated;

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        isDeleted = false;
        timeCreated = LocalDateTime.now();
        timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}
