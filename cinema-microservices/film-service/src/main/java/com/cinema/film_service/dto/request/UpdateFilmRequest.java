package com.cinema.film_service.dto.request;

import com.cinema.Enum.FilmEnum;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateFilmRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(min = 1, max = 255, message = "Tiêu đề không vượt quá 255 ký tự")
    String title;

    @Size(max = 100, message = "Tên đạo diễn không vượt quá 100 ký tự")
    String director;

    List<UUID> typeIds;

    List<UUID> actorIds;

    @NotNull(message = "Ngày phát hành không được để trống")
    @PastOrPresent(message = "Ngày phát hành không được ở trong tương lai")
    LocalDate releaseDate;

    String description;

    @NotNull(message = "Thời lượng phim không được để trống")
    @Positive(message = "Thời lượng phim phải là số dương")
    Integer duration;

    String poster;

    String trailer;

    @NotBlank(message = "Quốc gia không được để trống")
    @Size(max = 50, message = "Quốc gia không vượt quá 50 ký tự")
    String country;

    @NotBlank(message = "Ngôn ngữ không được để trống")
    @Size(max = 50, message = "Ngôn ngữ không vượt quá 50 ký tự")
    String language;

    @NotNull(message = "Phân loại độ tuổi không được để trống")
    FilmEnum.AgeRating ageRating;

    @NotNull(message = "Trạng thái không được để trống")
    FilmEnum.FilmStatus status;
}
