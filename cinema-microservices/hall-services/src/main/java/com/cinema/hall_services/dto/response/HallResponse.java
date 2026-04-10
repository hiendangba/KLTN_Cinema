package com.cinema.hall_services.dto.response;

import com.cinema.Enum.HallEnum;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HallResponse {
    UUID id;
    UUID cinemaId;
    CinemaResponse cinemaResponse;
    String name;
    JsonNode layoutJson;
    HallEnum.HallStatus status;
    boolean isDeleted;
    LocalDateTime timeCreated;
    LocalDateTime timeUpdated;
}
