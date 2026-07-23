package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PaymentLoyaltyOutbox;
import com.cinema.payment_service.enums.PaymentLoyaltyOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentLoyaltyOutboxRepository extends JpaRepository<PaymentLoyaltyOutbox, UUID> {

    Optional<PaymentLoyaltyOutbox> findByPaymentTransactionId(UUID paymentTransactionId);

    boolean existsByPaymentTransactionId(UUID paymentTransactionId);

    List<PaymentLoyaltyOutbox> findTop50ByStatusAndNextAttemptAtBeforeOrderByTimeCreatedAsc(
            PaymentLoyaltyOutboxStatus status,
            LocalDateTime nextAttemptAt);
}
