package cinema.seat_service.repository;

import cinema.seat_service.entity.HallLayoutCell;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HallLayoutCellRepository extends JpaRepository<HallLayoutCell, UUID> {
    List<HallLayoutCell> findAllByHallId(UUID hallId);

    List<HallLayoutCell> findAllByHallIdAndIsDeletedFalseOrderByRowAscColAsc(UUID hallId);
}
