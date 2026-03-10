package com.cinema.film_service.entity;

import com.cinema.Enum.FilmEnum;
import com.github.f4b6a3.uuid.UuidCreator;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "films", indexes = {
        @Index(name = "idx_title", columnList = "title"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_release_date", columnList = "release_date"),
        @Index(name = "idx_country", columnList = "country"),
        @Index(name = "idx_language", columnList = "language"),
        @Index(name = "idx_is_deleted", columnList = "is_deleted")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_film_title_release_date", columnNames = {"title", "release_date"})
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

    @Column(columnDefinition = "text")
    String actor;

    @Column(length = 50)
    String type;

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
        status = FilmEnum.FilmStatus.COMING_SOON;
        timeCreated = LocalDateTime.now();
        timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}
