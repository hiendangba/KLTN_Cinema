package com.cinema.booking_service.repository;

import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdAndIsDeletedFalse(UUID id);

    List<Booking> findAllByUserIdAndIsDeletedFalseOrderByTimeCreatedDesc(UUID userId);

    List<Booking> findAllByCinemaIdAndIsDeletedFalseOrderByTimeCreatedDesc(UUID cinemaId);

    boolean existsByShowtimeIdAndIsDeletedFalseAndBookingStatusIn(UUID showtimeId, Collection<BookingStatus> statuses);

    boolean existsByShowtimeIdInAndIsDeletedFalseAndBookingStatusIn(
            Collection<UUID> showtimeIds,
            Collection<BookingStatus> statuses);
}
