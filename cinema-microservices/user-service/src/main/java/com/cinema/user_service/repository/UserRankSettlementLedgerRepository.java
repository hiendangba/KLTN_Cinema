package com.cinema.user_service.repository;

import com.cinema.user_service.entity.UserRankSettlementLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRankSettlementLedgerRepository extends JpaRepository<UserRankSettlementLedger, UUID> {
    boolean existsByPaymentTransactionIdAndSettlementType(UUID paymentTransactionId, String settlementType);
}
