package com.cinema.hall_service.dto.request;

import com.cinema.Enum.HallEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateHallRequest {
    @NotBlank(message = "Hall name is required")
    String name;

    @NotNull(message = "status is required")
    HallEnum.HallStatus status;

    @Valid
    @NotNull(message = "layoutDefinition is required")
    HallLayoutDefinitionRequest layoutDefinition;

    @NotNull(message = "cinemaId is required")
    UUID cinemaId;

    @NotNull(message = "imagePaths is required")
    @Builder.Default
    List<String> imagePaths = new ArrayList<>();
}
