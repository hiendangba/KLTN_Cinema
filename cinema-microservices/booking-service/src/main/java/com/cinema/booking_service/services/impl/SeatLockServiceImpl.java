package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.services.SeatLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatLockServiceImpl implements SeatLockService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLockSeats(UUID showtimeId, List<String> seatCodes, UUID bookingId, Duration ttl) {
        List<String> acquiredKeys = new ArrayList<>();
        String bookingRef = bookingId.toString();

        for (String seatCode : seatCodes) {
            String key = buildSeatLockKey(showtimeId, seatCode);
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, bookingRef, ttl);
            if (Boolean.TRUE.equals(locked)) {
                acquiredKeys.add(key);
                continue;
            }

            for (String acquiredKey : acquiredKeys) {
                redisTemplate.delete(acquiredKey);
            }
            return false;
        }

        return true;
    }

    @Override
    public void releaseSeats(UUID showtimeId, List<String> seatCodes) {
        if (seatCodes == null || seatCodes.isEmpty()) {
            return;
        }
        List<String> keys = seatCodes.stream()
                .map(seatCode -> buildSeatLockKey(showtimeId, seatCode))
                .toList();
        redisTemplate.delete(keys);
    }

    private String buildSeatLockKey(UUID showtimeId, String seatCode) {
        return "booking:seat-lock:" + showtimeId + ":" + normalizeSeatCode(seatCode);
    }

    private String normalizeSeatCode(String seatCode) {
        return seatCode == null ? "" : seatCode.trim().toUpperCase(Locale.ROOT);
    }
}

