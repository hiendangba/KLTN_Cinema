package com.cinema.payment_service.services;

import com.cinema.messaging.LoyaltyPointsQueueNames;
import com.cinema.messaging.LoyaltyPointsSyncEvent;
import com.cinema.payment_service.entity.PaymentLoyaltyOutbox;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.enums.PaymentLoyaltyOutboxStatus;
import com.cinema.payment_service.repository.PaymentLoyaltyOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLoyaltyOutboxService {

    private static final long MAX_BACKOFF_SECONDS = 300L;
    private static final long BASE_BACKOFF_SECONDS = 15L;

    private final PaymentLoyaltyOutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueIfNeeded(PaymentTransaction transaction, String source) {
        if (transaction == null || transaction.getId() == null || transaction.getUserId() == null) {
            return;
        }
        long usedPoints = normalizeLong(transaction.getLoyaltyPointsUsed());
        long earnedPoints = normalizeLong(transaction.getLoyaltyPointsEarned());
        if (usedPoints <= 0 && earnedPoints <= 0) {
            log.info(
                    "LOYALTY_OUTBOX_SKIP_NO_POINTS transactionId={} bookingId={} userId={} source={}",
                    transaction.getId(),
                    transaction.getBookingId(),
                    transaction.getUserId(),
                    source);
            return;
        }
        if (outboxRepository.existsByPaymentTransactionId(transaction.getId())) {
            log.info(
                    "LOYALTY_OUTBOX_SKIP_ALREADY_EXISTS transactionId={} bookingId={} userId={} source={}",
                    transaction.getId(),
                    transaction.getBookingId(),
                    transaction.getUserId(),
                    source);
            return;
        }

        LoyaltyPointsSyncEvent event = buildEvent(transaction, source);
        PaymentLoyaltyOutbox outbox = new PaymentLoyaltyOutbox();
        outbox.setPaymentTransactionId(transaction.getId());
        outbox.setBookingId(transaction.getBookingId());
        outbox.setUserId(transaction.getUserId());
        outbox.setLoyaltyPointsUsed(usedPoints);
        outbox.setLoyaltyPointsEarned(earnedPoints);
        outbox.setSource(StringUtils.hasText(source) ? source : "unknown");
        outbox.setEventPayloadJson(writePayload(event));
        outbox.setStatus(PaymentLoyaltyOutboxStatus.PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(LocalDateTime.now());
        outboxRepository.save(outbox);
        log.info(
                "LOYALTY_OUTBOX_ENQUEUED transactionId={} bookingId={} userId={} used={} earned={} source={}",
                outbox.getPaymentTransactionId(),
                outbox.getBookingId(),
                outbox.getUserId(),
                outbox.getLoyaltyPointsUsed(),
                outbox.getLoyaltyPointsEarned(),
                outbox.getSource());
    }

    @Transactional
    public int publishDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentLoyaltyOutbox> dueMessages = outboxRepository
                .findTop50ByStatusAndNextAttemptAtBeforeOrderByTimeCreatedAsc(
                        PaymentLoyaltyOutboxStatus.PENDING,
                        now);
        int publishedCount = 0;
        for (PaymentLoyaltyOutbox outbox : dueMessages) {
            try {
                LoyaltyPointsSyncEvent event = buildEvent(outbox);
                rabbitTemplate.convertAndSend(
                        LoyaltyPointsQueueNames.EXCHANGE,
                        LoyaltyPointsQueueNames.ROUTING_KEY,
                        event);
                outbox.setStatus(PaymentLoyaltyOutboxStatus.PUBLISHED);
                outbox.setPublishedAt(now);
                outbox.setLastError(null);
                outbox.setNextAttemptAt(null);
                outbox.setLastAttemptAt(now);
                publishedCount++;
                log.info(
                        "LOYALTY_OUTBOX_PUBLISHED transactionId={} bookingId={} userId={} attempts={}",
                        outbox.getPaymentTransactionId(),
                        outbox.getBookingId(),
                        outbox.getUserId(),
                        outbox.getAttemptCount());
            } catch (Exception ex) {
                int nextAttemptCount = (outbox.getAttemptCount() == null ? 0 : outbox.getAttemptCount()) + 1;
                outbox.setAttemptCount(nextAttemptCount);
                outbox.setLastAttemptAt(now);
                outbox.setLastError(ex.getClass().getSimpleName() + ": " + safeMessage(ex.getMessage()));
                outbox.setNextAttemptAt(now.plusSeconds(calculateBackoffSeconds(nextAttemptCount)));
                log.warn(
                        "LOYALTY_OUTBOX_PUBLISH_FAILED transactionId={} bookingId={} userId={} attempts={} nextAttemptAt={} error={}",
                        outbox.getPaymentTransactionId(),
                        outbox.getBookingId(),
                        outbox.getUserId(),
                        nextAttemptCount,
                        outbox.getNextAttemptAt(),
                        outbox.getLastError(),
                        ex);
            }
        }
        return publishedCount;
    }

    private LoyaltyPointsSyncEvent buildEvent(PaymentTransaction transaction, String source) {
        return new LoyaltyPointsSyncEvent(
                UUID.randomUUID(),
                transaction.getId(),
                transaction.getBookingId(),
                transaction.getUserId(),
                normalizeLong(transaction.getLoyaltyPointsUsed()),
                normalizeLong(transaction.getLoyaltyPointsEarned()),
                StringUtils.hasText(source) ? source : "unknown",
                LocalDateTime.now());
    }

    private LoyaltyPointsSyncEvent buildEvent(PaymentLoyaltyOutbox outbox) {
        try {
            if (StringUtils.hasText(outbox.getEventPayloadJson())) {
                return objectMapper.readValue(outbox.getEventPayloadJson(), LoyaltyPointsSyncEvent.class);
            }
        } catch (Exception ex) {
            log.warn(
                    "LOYALTY_OUTBOX_PAYLOAD_REBUILD_FAILED transactionId={} bookingId={} userId={} reason=deserialize_failed",
                    outbox.getPaymentTransactionId(),
                    outbox.getBookingId(),
                    outbox.getUserId(),
                    ex);
        }
        return new LoyaltyPointsSyncEvent(
                UUID.randomUUID(),
                outbox.getPaymentTransactionId(),
                outbox.getBookingId(),
                outbox.getUserId(),
                normalizeLong(outbox.getLoyaltyPointsUsed()),
                normalizeLong(outbox.getLoyaltyPointsEarned()),
                outbox.getSource(),
                outbox.getPublishedAt() != null ? outbox.getPublishedAt() : LocalDateTime.now());
    }

    private String writePayload(LoyaltyPointsSyncEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize loyalty points event", ex);
        }
    }

    private long normalizeLong(Long value) {
        return value == null || value < 0 ? 0L : value;
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
