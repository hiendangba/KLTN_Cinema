package com.cinema.hall_service.repository;

import com.cinema.hall_service.entity.HallImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HallImageRepository extends JpaRepository<HallImage, UUID> {
    List<HallImage> findAllByHall_Id(UUID hallId);

    List<HallImage> findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(UUID hallId);

    boolean existsByHall_IdAndImagePathAndIsDeletedFalse(UUID hallId, String imagePath);

    Optional<HallImage> findByIdAndHall_IdAndIsDeletedFalse(UUID imageId, UUID hallId);
}
