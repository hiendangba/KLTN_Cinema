package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PaymentTransactionPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentTransactionPromotionRepository extends JpaRepository<PaymentTransactionPromotion, UUID> {

    List<PaymentTransactionPromotion> findAllByPaymentTransactionId(UUID paymentTransactionId);

    List<PaymentTransactionPromotion> findAllByPaymentTransactionIdIn(Collection<UUID> paymentTransactionIds, Sort sort);

    void deleteByPaymentTransactionId(UUID paymentTransactionId);
}
