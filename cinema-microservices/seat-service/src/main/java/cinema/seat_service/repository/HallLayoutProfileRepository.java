package cinema.seat_service.repository;

import cinema.seat_service.entity.HallLayoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HallLayoutProfileRepository extends JpaRepository<HallLayoutProfile, UUID> {
    Optional<HallLayoutProfile> findByHallIdAndIsDeletedFalse(UUID hallId);
}
