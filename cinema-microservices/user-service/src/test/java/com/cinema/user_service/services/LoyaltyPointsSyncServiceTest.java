package com.cinema.user_service.services;

import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.messaging.LoyaltyPointsSyncEvent;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.entity.UserLoyaltyLedger;
import com.cinema.user_service.repository.UserLoyaltyLedgerRepository;
import com.cinema.user_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyPointsSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserLoyaltyLedgerRepository userLoyaltyLedgerRepository;

    @InjectMocks
    private LoyaltyPointsSyncService loyaltyPointsSyncService;

    @Test
    void apply_shouldUpdateUserBalanceAndCreateLedger() {
        UUID transactionId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LoyaltyPointsSyncEvent event = new LoyaltyPointsSyncEvent(
                UUID.randomUUID(),
                transactionId,
                bookingId,
                userId,
                200L,
                50L,
                "webhook",
                LocalDateTime.now());

        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setName("User");
        user.setRole(UserEnum.UserRole.CUSTOMER);
        user.setIsDeleted(false);
        user.setLoyaltyPoints(1000L);

        when(userLoyaltyLedgerRepository.existsByPaymentTransactionId(transactionId)).thenReturn(false);
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.updateLoyaltyPoints(any(UUID.class), any(Long.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(userLoyaltyLedgerRepository.save(any(UserLoyaltyLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        loyaltyPointsSyncService.apply(event);

        ArgumentCaptor<Long> loyaltyPointsCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<LocalDateTime> timeUpdatedCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).updateLoyaltyPoints(
                org.mockito.ArgumentMatchers.eq(userId),
                loyaltyPointsCaptor.capture(),
                timeUpdatedCaptor.capture());
        assertEquals(850L, loyaltyPointsCaptor.getValue());

        ArgumentCaptor<UserLoyaltyLedger> ledgerCaptor = ArgumentCaptor.forClass(UserLoyaltyLedger.class);
        verify(userLoyaltyLedgerRepository).save(ledgerCaptor.capture());
        assertEquals(transactionId, ledgerCaptor.getValue().getPaymentTransactionId());
        assertEquals(200L, ledgerCaptor.getValue().getLoyaltyPointsUsed());
        assertEquals(50L, ledgerCaptor.getValue().getLoyaltyPointsEarned());
    }

    @Test
    void apply_shouldSkipDuplicateTransaction() {
        UUID transactionId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LoyaltyPointsSyncEvent event = new LoyaltyPointsSyncEvent(
                UUID.randomUUID(),
                transactionId,
                bookingId,
                userId,
                200L,
                50L,
                "webhook",
                LocalDateTime.now());

        when(userLoyaltyLedgerRepository.existsByPaymentTransactionId(transactionId)).thenReturn(true);

        loyaltyPointsSyncService.apply(event);

        verify(userRepository, never()).updateLoyaltyPoints(
                any(UUID.class),
                any(Long.class),
                any(LocalDateTime.class));
        verify(userLoyaltyLedgerRepository, never()).save(any(UserLoyaltyLedger.class));
    }

    @Test
    void apply_shouldRejectWhenBalanceIsInsufficient() {
        UUID transactionId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LoyaltyPointsSyncEvent event = new LoyaltyPointsSyncEvent(
                UUID.randomUUID(),
                transactionId,
                bookingId,
                userId,
                2000L,
                0L,
                "webhook",
                LocalDateTime.now());

        User user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setName("User");
        user.setRole(UserEnum.UserRole.CUSTOMER);
        user.setIsDeleted(false);
        user.setLoyaltyPoints(1000L);

        when(userLoyaltyLedgerRepository.existsByPaymentTransactionId(transactionId)).thenReturn(false);
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> loyaltyPointsSyncService.apply(event));
        assertEquals(ErrorCode.LOYALTY_POINTS_INSUFFICIENT, exception.getErrorCode());
    }
}
