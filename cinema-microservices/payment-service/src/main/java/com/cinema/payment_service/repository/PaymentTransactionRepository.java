package com.cinema.payment_service.repository;

import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findFirstByBookingIdOrderByTimeCreatedDesc(UUID bookingId);

    Optional<PaymentTransaction> findFirstByBookingIdAndUserIdOrderByTimeCreatedDesc(UUID bookingId, UUID userId);

    Optional<PaymentTransaction> findFirstByBookingIdAndStatusOrderByTimeCreatedDesc(UUID bookingId,
                                                                                      PaymentTransactionStatus status);

    Optional<PaymentTransaction> findByOrderInvoiceNumber(String orderInvoiceNumber);

    List<PaymentTransaction> findAllByStatusAndExpiresAtBefore(PaymentTransactionStatus status,
                                                               LocalDateTime now);

    List<PaymentTransaction> findAllByTimeCreatedBetween(LocalDateTime from, LocalDateTime to);

    List<PaymentTransaction> findAllByCinemaIdIsNotNull();

    List<PaymentTransaction> findAllByCinemaIdIn(Collection<UUID> cinemaIds);
}
