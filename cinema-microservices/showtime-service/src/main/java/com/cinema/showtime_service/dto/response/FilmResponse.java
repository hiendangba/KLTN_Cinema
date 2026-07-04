package com.cinema.showtime_service.dto.response;

import com.cinema.Enum.FilmEnum;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FilmResponse {

    UUID id;

    String title;

    String director;

    @Builder.Default
    List<FilmTypeResponse> types = List.of();

    @Builder.Default
    List<ActorResponse> actors = List.of();

    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate releaseDate;

    String description;

    Integer duration;

    String poster;

    String trailer;

    String country;

    String language;

    FilmEnum.AgeRating ageRating;

    FilmEnum.FilmStatus status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timeCreated;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timeUpdated;
}
