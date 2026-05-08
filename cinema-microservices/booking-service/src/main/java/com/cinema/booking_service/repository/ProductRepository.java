package com.cinema.booking_service.repository;

import com.cinema.booking_service.entity.Product;
import com.cinema.booking_service.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndIsDeletedFalse(UUID id);

    List<Product> findAllByCinemaIdAndIsDeletedFalseOrderByTimeCreatedDesc(UUID cinemaId);

    List<Product> findAllByIdInAndIsDeletedFalse(Set<UUID> ids);

    List<Product> findAllByCinemaIdAndStatusAndIsDeletedFalseOrderByTimeCreatedDesc(UUID cinemaId, ProductStatus status);

    boolean existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(UUID cinemaId, String name);

    boolean existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(UUID cinemaId, String name, UUID id);
}
