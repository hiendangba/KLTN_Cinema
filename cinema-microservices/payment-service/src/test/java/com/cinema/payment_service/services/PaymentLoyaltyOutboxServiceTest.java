package com.cinema.payment_service.services;

import com.cinema.messaging.LoyaltyPointsQueueNames;
import com.cinema.messaging.LoyaltyPointsSyncEvent;
import com.cinema.payment_service.entity.PaymentLoyaltyOutbox;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.enums.PaymentLoyaltyOutboxStatus;
import com.cinema.payment_service.repository.PaymentLoyaltyOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentLoyaltyOutboxServiceTest {

    @Mock
    private PaymentLoyaltyOutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentLoyaltyOutboxService paymentLoyaltyOutboxService;

    @Test
    void enqueueIfNeeded_shouldPersistPendingOutbox() throws Exception {
        PaymentTransaction transaction = buildTransaction();
        when(outboxRepository.existsByPaymentTransactionId(transaction.getId())).thenReturn(false);
        when(objectMapper.writeValueAsString(any(LoyaltyPointsSyncEvent.class))).thenReturn("{\"mock\":true}");
        when(outboxRepository.save(any(PaymentLoyaltyOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentLoyaltyOutboxService.enqueueIfNeeded(transaction, "webhook");

        ArgumentCaptor<PaymentLoyaltyOutbox> captor = ArgumentCaptor.forClass(PaymentLoyaltyOutbox.class);
        verify(outboxRepository).save(captor.capture());
        PaymentLoyaltyOutbox saved = captor.getValue();
        assertEquals(transaction.getId(), saved.getPaymentTransactionId());
        assertEquals(transaction.getBookingId(), saved.getBookingId());
        assertEquals(transaction.getUserId(), saved.getUserId());
        assertEquals(200L, saved.getLoyaltyPointsUsed());
        assertEquals(50L, saved.getLoyaltyPointsEarned());
        assertEquals(PaymentLoyaltyOutboxStatus.PENDING, saved.getStatus());
        assertNotNull(saved.getNextAttemptAt());
    }

    @Test
    void publishDueEvents_shouldPublishAndMarkAsPublished() throws Exception {
        PaymentLoyaltyOutbox outbox = new PaymentLoyaltyOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setPaymentTransactionId(UUID.randomUUID());
        outbox.setBookingId(UUID.randomUUID());
        outbox.setUserId(UUID.randomUUID());
        outbox.setLoyaltyPointsUsed(200L);
        outbox.setLoyaltyPointsEarned(50L);
        outbox.setSource("webhook");
        outbox.setEventPayloadJson("{\"eventId\":\"" + UUID.randomUUID() + "\"}");
        outbox.setStatus(PaymentLoyaltyOutboxStatus.PENDING);
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));

        when(outboxRepository.findTop50ByStatusAndNextAttemptAtBeforeOrderByTimeCreatedAsc(
                eq(PaymentLoyaltyOutboxStatus.PENDING),
                any(LocalDateTime.class))).thenReturn(List.of(outbox));
        when(objectMapper.readValue(outbox.getEventPayloadJson(), LoyaltyPointsSyncEvent.class)).thenReturn(
                new LoyaltyPointsSyncEvent(
                        UUID.randomUUID(),
                        outbox.getPaymentTransactionId(),
                        outbox.getBookingId(),
                        outbox.getUserId(),
                        outbox.getLoyaltyPointsUsed(),
                        outbox.getLoyaltyPointsEarned(),
                        outbox.getSource(),
                        LocalDateTime.now()));

        int published = paymentLoyaltyOutboxService.publishDueEvents();

        assertEquals(1, published);
        verify(rabbitTemplate).convertAndSend(
                eq(LoyaltyPointsQueueNames.EXCHANGE),
                eq(LoyaltyPointsQueueNames.ROUTING_KEY),
                any(LoyaltyPointsSyncEvent.class));
        assertEquals(PaymentLoyaltyOutboxStatus.PUBLISHED, outbox.getStatus());
        assertNotNull(outbox.getPublishedAt());
    }

    private PaymentTransaction buildTransaction() {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setBookingId(UUID.randomUUID());
        transaction.setUserId(UUID.randomUUID());
        transaction.setLoyaltyPointsUsed(200L);
        transaction.setLoyaltyPointsEarned(50L);
        transaction.setAmount(BigDecimal.valueOf(150000));
        return transaction;
    }
}
