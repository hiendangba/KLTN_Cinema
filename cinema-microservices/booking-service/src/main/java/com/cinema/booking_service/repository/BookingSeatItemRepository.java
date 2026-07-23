package com.cinema.booking_service.repository;

import com.cinema.booking_service.entity.BookingSeatItem;
import com.cinema.booking_service.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingSeatItemRepository extends JpaRepository<BookingSeatItem, UUID> {
    @Query("""
            SELECT COUNT(bsi) > 0
            FROM BookingSeatItem bsi
            JOIN bsi.booking b
            WHERE b.showtimeId = :showtimeId
              AND b.isDeleted = false
              AND b.bookingStatus IN :statuses
              AND b.reservedUntil > :now
              AND UPPER(bsi.seatCode) IN :seatCodes
            """)
    boolean existsLockedSeatCodes(
            @Param("showtimeId") UUID showtimeId,
            @Param("seatCodes") Collection<String> seatCodes,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("now") LocalDateTime now);

    @Query("""
            SELECT UPPER(bsi.seatCode)
            FROM BookingSeatItem bsi
            JOIN bsi.booking b
            WHERE b.showtimeId = :showtimeId
              AND b.isDeleted = false
              AND b.bookingStatus IN :statuses
              AND UPPER(bsi.seatCode) IN :seatCodes
            """)
    List<String> findSeatCodesByShowtimeAndStatuses(
            @Param("showtimeId") UUID showtimeId,
            @Param("seatCodes") Collection<String> seatCodes,
            @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
            SELECT UPPER(bsi.seatCode)
            FROM BookingSeatItem bsi
            JOIN bsi.booking b
            WHERE b.showtimeId = :showtimeId
              AND b.isDeleted = false
              AND b.bookingStatus IN :statuses
              AND b.reservedUntil > :now
              AND UPPER(bsi.seatCode) IN :seatCodes
            """)
    List<String> findActiveLockedSeatCodesByShowtimeAndStatuses(
            @Param("showtimeId") UUID showtimeId,
            @Param("seatCodes") Collection<String> seatCodes,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("now") LocalDateTime now);
}
