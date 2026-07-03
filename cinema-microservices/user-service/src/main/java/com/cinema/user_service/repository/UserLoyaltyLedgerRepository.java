package com.cinema.user_service.repository;

import com.cinema.user_service.entity.UserLoyaltyLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserLoyaltyLedgerRepository extends JpaRepository<UserLoyaltyLedger, UUID> {

    boolean existsByPaymentTransactionId(UUID paymentTransactionId);
}
