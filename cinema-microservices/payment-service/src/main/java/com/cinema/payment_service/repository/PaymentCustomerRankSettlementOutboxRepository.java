package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PaymentCustomerRankSettlementOutbox;
import com.cinema.payment_service.enums.PaymentLoyaltyOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PaymentCustomerRankSettlementOutboxRepository
        extends JpaRepository<PaymentCustomerRankSettlementOutbox, UUID> {
    boolean existsByPaymentTransactionIdAndSettlementType(UUID paymentTransactionId, String settlementType);

    List<PaymentCustomerRankSettlementOutbox> findTop50ByStatusAndNextAttemptAtBeforeOrderByTimeCreatedAsc(
            PaymentLoyaltyOutboxStatus status,
            LocalDateTime now);
}
