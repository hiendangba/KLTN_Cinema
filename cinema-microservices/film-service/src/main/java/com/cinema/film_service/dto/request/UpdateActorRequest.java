package com.cinema.film_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateActorRequest {

    @NotBlank(message = "Tên diễn viên không được để trống")
    @Size(max = 255, message = "Tên diễn viên không vượt quá 255 ký tự")
    String name;

    @Min(value = 1800, message = "Năm sinh diễn viên không hợp lệ")
    Integer birthYear;

    @Size(max = 500, message = "Quê quán không vượt quá 500 ký tự")
    String hometown;

    @Size(max = 1000, message = "Ảnh diễn viên không vượt quá 1000 ký tự")
    String avatarUrl;
}
