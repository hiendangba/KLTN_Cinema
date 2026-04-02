package com.cinema.showtime_service.dto.response;

import com.cinema.Enum.ShowTimeEnum;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShowTimeResponse {
    UUID id;
    UUID hallId;
    UUID filmId;
    LocalDateTime startDateTime;
    LocalDateTime endDateTime;
    ShowTimeEnum.ShowTimeStatus status;
    boolean isDeleted;
    LocalDateTime timeCreated;
    LocalDateTime timeUpdated;
}