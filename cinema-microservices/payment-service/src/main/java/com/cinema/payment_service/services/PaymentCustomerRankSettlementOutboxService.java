package com.cinema.payment_service.services;

import com.cinema.messaging.CustomerRankSettlementEvent;
import com.cinema.messaging.CustomerRankSettlementQueueNames;
import com.cinema.payment_service.entity.PaymentCustomerRankSettlementOutbox;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.enums.PaymentLoyaltyOutboxStatus;
import com.cinema.payment_service.repository.PaymentCustomerRankSettlementOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCustomerRankSettlementOutboxService {

    private static final long MAX_BACKOFF_SECONDS = 300L;
    private static final long BASE_BACKOFF_SECONDS = 15L;

    private final PaymentCustomerRankSettlementOutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueIfNeeded(
            PaymentTransaction transaction,
            BigDecimal settlementAmount,
            String settlementType,
            String source) {
        if (transaction == null
                || transaction.getId() == null
                || transaction.getUserId() == null
                || settlementAmount == null
                || !StringUtils.hasText(settlementType)) {
            return;
        }

        String normalizedType = normalizeType(settlementType);
        if (outboxRepository.existsByPaymentTransactionIdAndSettlementType(
                transaction.getId(),
                normalizedType)) {
            log.info(
                    "CUSTOMER_RANK_OUTBOX_SKIP_ALREADY_EXISTS transactionId={} bookingId={} userId={} type={} source={}",
                    transaction.getId(),
                    transaction.getBookingId(),
                    transaction.getUserId(),
                    normalizedType,
                    source);
            return;
        }

        CustomerRankSettlementEvent event = buildEvent(transaction, settlementAmount, normalizedType, source);
        PaymentCustomerRankSettlementOutbox outbox = new PaymentCustomerRankSettlementOutbox();
        outbox.setPaymentTransactionId(transaction.getId());
        outbox.setBookingId(transaction.getBookingId());
        outbox.setUserId(transaction.getUserId());
        outbox.setSettlementAmount(normalizeAmount(settlementAmount));
        outbox.setSettlementType(normalizedType);
        outbox.setSource(StringUtils.hasText(source) ? source : "unknown");
        outbox.setEventPayloadJson(writePayload(event));
        outbox.setStatus(PaymentLoyaltyOutboxStatus.PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(LocalDateTime.now());
        outboxRepository.save(outbox);
        log.info(
                "CUSTOMER_RANK_OUTBOX_ENQUEUED transactionId={} bookingId={} userId={} type={} amount={} source={}",
                outbox.getPaymentTransactionId(),
                outbox.getBookingId(),
                outbox.getUserId(),
                outbox.getSettlementType(),
                outbox.getSettlementAmount(),
                outbox.getSource());
    }

    @Transactional
    public int publishDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentCustomerRankSettlementOutbox> dueMessages = outboxRepository
                .findTop50ByStatusAndNextAttemptAtBeforeOrderByTimeCreatedAsc(
                        PaymentLoyaltyOutboxStatus.PENDING,
                        now);

        int publishedCount = 0;
        for (PaymentCustomerRankSettlementOutbox outbox : dueMessages) {
            try {
                CustomerRankSettlementEvent event = buildEvent(outbox);
                rabbitTemplate.convertAndSend(
                        CustomerRankSettlementQueueNames.EXCHANGE,
                        CustomerRankSettlementQueueNames.ROUTING_KEY,
                        event);
                outbox.setStatus(PaymentLoyaltyOutboxStatus.PUBLISHED);
                outbox.setPublishedAt(now);
                outbox.setLastError(null);
                outbox.setNextAttemptAt(null);
                outbox.setLastAttemptAt(now);
                publishedCount++;
                log.info(
                        "CUSTOMER_RANK_OUTBOX_PUBLISHED transactionId={} bookingId={} userId={} type={} attempts={}",
                        outbox.getPaymentTransactionId(),
                        outbox.getBookingId(),
                        outbox.getUserId(),
                        outbox.getSettlementType(),
                        outbox.getAttemptCount());
            } catch (Exception ex) {
                int nextAttemptCount = (outbox.getAttemptCount() == null ? 0 : outbox.getAttemptCount()) + 1;
                outbox.setAttemptCount(nextAttemptCount);
                outbox.setLastAttemptAt(now);
                outbox.setLastError(ex.getClass().getSimpleName() + ": " + safeMessage(ex.getMessage()));
                outbox.setNextAttemptAt(now.plusSeconds(calculateBackoffSeconds(nextAttemptCount)));
                log.warn(
                        "CUSTOMER_RANK_OUTBOX_PUBLISH_FAILED transactionId={} bookingId={} userId={} type={} attempts={} nextAttemptAt={} error={}",
                        outbox.getPaymentTransactionId(),
                        outbox.getBookingId(),
                        outbox.getUserId(),
                        outbox.getSettlementType(),
                        nextAttemptCount,
                        outbox.getNextAttemptAt(),
                        outbox.getLastError(),
                        ex);
            }
        }
        return publishedCount;
    }

    private CustomerRankSettlementEvent buildEvent(
            PaymentTransaction transaction,
            BigDecimal settlementAmount,
            String settlementType,
            String source) {
        return new CustomerRankSettlementEvent(
                UUID.randomUUID(),
                transaction.getId(),
                transaction.getBookingId(),
                transaction.getUserId(),
                normalizeAmount(settlementAmount),
                settlementType,
                StringUtils.hasText(source) ? source : "unknown",
                LocalDateTime.now());
    }

    private CustomerRankSettlementEvent buildEvent(PaymentCustomerRankSettlementOutbox outbox) {
        try {
            if (StringUtils.hasText(outbox.getEventPayloadJson())) {
                return objectMapper.readValue(outbox.getEventPayloadJson(), CustomerRankSettlementEvent.class);
            }
        } catch (Exception ex) {
            log.warn(
                    "CUSTOMER_RANK_OUTBOX_PAYLOAD_REBUILD_FAILED transactionId={} bookingId={} userId={} type={} reason=deserialize_failed",
                    outbox.getPaymentTransactionId(),
                    outbox.getBookingId(),
                    outbox.getUserId(),
                    outbox.getSettlementType(),
                    ex);
        }
        return new CustomerRankSettlementEvent(
                UUID.randomUUID(),
                outbox.getPaymentTransactionId(),
                outbox.getBookingId(),
                outbox.getUserId(),
                normalizeAmount(outbox.getSettlementAmount()),
                normalizeType(outbox.getSettlementType()),
                outbox.getSource(),
                outbox.getPublishedAt() != null ? outbox.getPublishedAt() : LocalDateTime.now());
    }

    private String writePayload(CustomerRankSettlementEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize customer rank settlement event", ex);
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP) : amount.setScale(0, RoundingMode.HALF_UP);
    }

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private long calculateBackoffSeconds(int attemptCount) {
        int cappedAttempt = Math.min(Math.max(attemptCount, 1), 6);
        long backoff = BASE_BACKOFF_SECONDS * (1L << (cappedAttempt - 1));
        return Math.min(backoff, MAX_BACKOFF_SECONDS);
    }

    private String safeMessage(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }
}
