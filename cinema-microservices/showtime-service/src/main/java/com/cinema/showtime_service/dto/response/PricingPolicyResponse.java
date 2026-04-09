package com.cinema.showtime_service.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricingPolicyResponse {
    UUID id;
    String name;
    Long standardPrice;
    Long vipPrice;
    Long couplePrice;
    UUID cinemaId;
    boolean isDeleted;
    LocalDateTime timeCreated;
    LocalDateTime timeUpdated;
}
