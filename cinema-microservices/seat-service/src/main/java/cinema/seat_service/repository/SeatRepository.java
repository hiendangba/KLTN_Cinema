package cinema.seat_service.repository;

import cinema.seat_service.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findAllByHallIdAndIsDeletedFalseOrderByRowAscColAsc(UUID hallId);

    List<Seat> findAllByHallIdAndSeatCodeInAndIsDeletedFalse(UUID hallId, Collection<String> seatCodes);

    void deleteAllByHallId(UUID hallId);
}
