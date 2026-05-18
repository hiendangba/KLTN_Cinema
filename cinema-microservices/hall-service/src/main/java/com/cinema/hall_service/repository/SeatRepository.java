package com.cinema.hall_service.repository;

import com.cinema.hall_service.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findAllByHall_IdAndIsDeletedFalseOrderByRowAscColAsc(UUID hallId);

    boolean existsByHall_IdAndSeatCodeIgnoreCaseAndIsDeletedFalse(UUID hallId, String seatCode);

    List<Seat> findAllByHall_IdAndSeatCodeInAndIsDeletedFalse(UUID hallId, Collection<String> seatCodes);

    void deleteAllByHall_Id(UUID hallId);
}
