package com.cinema.hall_service.dto.response;

import com.cinema.Enum.HallEnum;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    List<SeatResponse> seats = new ArrayList<>();
    List<HallImageResponse> images = new ArrayList<>();
    HallEnum.HallStatus status;
    Boolean isDeleted;
    LocalDateTime timeCreated;
    LocalDateTime timeUpdated;
}
