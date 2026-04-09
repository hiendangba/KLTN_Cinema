package com.cinema.hall_services.dto.request;

import com.cinema.Enum.HallEnum;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HallCreateRequest {
    @NotNull(message = "Cinema id is required")
    UUID cinemaId;

    @NotBlank(message = "Hall name is required")
    String name;

    @NotNull(message = "Layout json is required")
    JsonNode layoutJson;

    HallEnum.HallStatus status;
}
