package com.cinema.film_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BatchFilmRequest {
    @NotEmpty(message = "Danh sách ID phim không được rỗng")
    private List<UUID> ids;
}
