package com.cinema.booking_service.services;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface SeatLockService {
    boolean tryLockSeats(UUID showtimeId, List<String> seatCodes, UUID bookingId, Duration ttl);

    void releaseSeats(UUID showtimeId, List<String> seatCodes);
}

