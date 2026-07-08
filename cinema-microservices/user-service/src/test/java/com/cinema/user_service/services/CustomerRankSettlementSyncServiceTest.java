package com.cinema.user_service.services;

import com.cinema.Enum.UserEnum;
import com.cinema.messaging.CustomerRankSettlementEvent;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.entity.UserRankSettlementLedger;
import com.cinema.user_service.repository.UserRankSettlementLedgerRepository;
import com.cinema.user_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRankSettlementSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRankSettlementLedgerRepository ledgerRepository;

    @InjectMocks
    private CustomerRankSettlementSyncService settlementSyncService;

    @Test
    void apply_shouldUpdateLifetimePaidAmountAndCreateLedger() {
        UUID transactionId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime occurredAt = LocalDateTime.now();
        CustomerRankSettlementEvent event = new CustomerRankSettlementEvent(
                UUID.randomUUID(),
                transactionId,
                bookingId,
                userId,
                BigDecimal.valueOf(949500),
                "PAID",
                "webhook",
                occurredAt);

        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setName("User");
        user.setRole(UserEnum.UserRole.CUSTOMER);
        user.setIsDeleted(false);
        user.setLifetimePaidAmount(BigDecimal.ZERO);

        when(ledgerRepository.existsByPaymentTransactionIdAndSettlementType(transactionId, "PAID"))
                .thenReturn(false);
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.updateLifetimePaidAmount(any(UUID.class), any(BigDecimal.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(ledgerRepository.save(any(UserRankSettlementLedger.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        settlementSyncService.apply(event);

        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(userRepository).updateLifetimePaidAmount(
                eq(userId),
                amountCaptor.capture(),
                any(LocalDateTime.class));
        assertEquals(BigDecimal.valueOf(949500), amountCaptor.getValue());

        ArgumentCaptor<UserRankSettlementLedger> ledgerCaptor = ArgumentCaptor.forClass(UserRankSettlementLedger.class);
        verify(ledgerRepository).save(ledgerCaptor.capture());
        assertEquals(transactionId, ledgerCaptor.getValue().getPaymentTransactionId());
        assertEquals(BigDecimal.valueOf(949500), ledgerCaptor.getValue().getSettlementAmount());
        assertEquals(occurredAt, ledgerCaptor.getValue().getOccurredAt());
    }

    @Test
    void apply_shouldSkipDuplicateTransaction() {
        UUID transactionId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CustomerRankSettlementEvent event = new CustomerRankSettlementEvent(
                UUID.randomUUID(),
                transactionId,
                bookingId,
                userId,
                BigDecimal.valueOf(949500),
                "PAID",
                "webhook",
                LocalDateTime.now());

        when(ledgerRepository.existsByPaymentTransactionIdAndSettlementType(transactionId, "PAID"))
                .thenReturn(true);

        settlementSyncService.apply(event);

        verify(userRepository, never()).updateLifetimePaidAmount(
                any(UUID.class),
                any(BigDecimal.class),
                any(LocalDateTime.class));
        verify(ledgerRepository, never()).save(any(UserRankSettlementLedger.class));
    }
}
