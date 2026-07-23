package com.cinema.user_service.repository;

import com.cinema.user_service.entity.CustomerRank;
import com.cinema.user_service.enums.CustomerRankStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRankRepository extends JpaRepository<CustomerRank, UUID> {
    boolean existsByCodeIgnoreCaseAndIsDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot(String code, UUID id);

    boolean existsByLevelAndIsDeletedFalse(Integer level);

    boolean existsByLevelAndIsDeletedFalseAndIdNot(Integer level, UUID id);

    List<CustomerRank> findAllByIsDeletedFalseOrderByLevelAsc();

    List<CustomerRank> findAllByIsDeletedFalseAndStatusOrderByLevelAsc(CustomerRankStatus status);

    Optional<CustomerRank> findFirstByIsDeletedFalseAndStatusAndMinLifetimeAmountLessThanEqualOrderByMinLifetimeAmountDescLevelDesc(
            CustomerRankStatus status,
            BigDecimal minLifetimeAmount);
}
