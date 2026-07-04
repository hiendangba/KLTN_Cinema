package com.cinema.booking_service.repository;

import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdAndIsDeletedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "seatItems")
    @Query("""
            SELECT b
            FROM Booking b
            WHERE b.id = :id
              AND b.isDeleted = false
            """)
    Optional<Booking> findLockedByIdAndIsDeletedFalse(@Param("id") UUID id);

    @Query("""
            SELECT b
            FROM Booking b
            WHERE b.userId = :userId
              AND b.isDeleted = false
              AND b.bookingStatus IN :statuses
              AND b.reservedUntil > :now
              AND (:showtimeId IS NULL OR b.showtimeId = :showtimeId)
              AND (:cinemaId IS NULL OR b.cinemaId = :cinemaId)
            ORDER BY b.timeCreated DESC
            """)
    List<Booking> findActiveBookingsByUser(
            @Param("userId") UUID userId,
            @Param("statuses") Collection<BookingStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("showtimeId") UUID showtimeId,
            @Param("cinemaId") UUID cinemaId,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "seatItems")
    @Query("""
            SELECT DISTINCT b
            FROM Booking b
            WHERE b.isDeleted = false
              AND b.bookingStatus IN :statuses
              AND b.reservedUntil <= :now
            ORDER BY b.reservedUntil ASC
            """)
    List<Booking> findDueBookingsForExpiration(
            @Param("now") LocalDateTime now,
            @Param("statuses") Collection<BookingStatus> statuses);

    boolean existsByShowtimeIdAndIsDeletedFalseAndBookingStatusIn(UUID showtimeId, Collection<BookingStatus> statuses);

    boolean existsByShowtimeIdInAndIsDeletedFalseAndBookingStatusIn(
            Collection<UUID> showtimeIds,
            Collection<BookingStatus> statuses);

    boolean existsByUserIdAndFilmIdAndIsDeletedFalseAndBookingStatusAndPaymentStatus(
            UUID userId,
            UUID filmId,
            BookingStatus bookingStatus,
            PaymentStatus paymentStatus);

    List<Booking> findAllByUserIdAndFilmIdAndIsDeletedFalseAndBookingStatusAndPaymentStatus(
            UUID userId,
            UUID filmId,
            BookingStatus bookingStatus,
            PaymentStatus paymentStatus);
}
