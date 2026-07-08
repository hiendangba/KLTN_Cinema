package com.cinema.user_service.services;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.messaging.LoyaltyPointsSyncEvent;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.entity.UserLoyaltyLedger;
import com.cinema.user_service.repository.UserLoyaltyLedgerRepository;
import com.cinema.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyPointsSyncService {

    private final UserRepository userRepository;
    private final UserLoyaltyLedgerRepository userLoyaltyLedgerRepository;

    @Transactional
    public void apply(LoyaltyPointsSyncEvent event) {
        validateEvent(event);
        if (userLoyaltyLedgerRepository.existsByPaymentTransactionId(event.paymentTransactionId())) {
            log.info(
                    "LOYALTY_SYNC_DUPLICATE_SKIP transactionId={} bookingId={} userId={} source={}",
                    event.paymentTransactionId(),
                    event.bookingId(),
                    event.userId(),
                    event.source());
            return;
        }

        User user = userRepository.findByIdAndIsDeletedFalse(event.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long currentPoints = user.getLoyaltyPoints() == null ? 0L : user.getLoyaltyPoints();
        long usedPoints = normalize(event.loyaltyPointsUsed());
        long earnedPoints = normalize(event.loyaltyPointsEarned());
        if (currentPoints < usedPoints) {
            throw new BusinessException(ErrorCode.LOYALTY_POINTS_INSUFFICIENT);
        }

        long remainingPoints = currentPoints - usedPoints;
        long nextPoints;
        try {
            nextPoints = Math.addExact(remainingPoints, earnedPoints);
        } catch (ArithmeticException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        UserLoyaltyLedger ledger = new UserLoyaltyLedger();
        ledger.setPaymentTransactionId(event.paymentTransactionId());
        ledger.setBookingId(event.bookingId());
        ledger.setUserId(event.userId());
        ledger.setLoyaltyPointsUsed(usedPoints);
        ledger.setLoyaltyPointsEarned(earnedPoints);
        ledger.setSource(StringUtils.hasText(event.source()) ? event.source() : "unknown");
        ledger.setOccurredAt(event.occurredAt() == null ? LocalDateTime.now() : event.occurredAt());
        ledger.setProcessedAt(LocalDateTime.now());
        userLoyaltyLedgerRepository.save(ledger);
        int updatedRows = userRepository.updateLoyaltyPoints(user.getId(), nextPoints, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        log.info(
                "LOYALTY_SYNC_APPLIED transactionId={} bookingId={} userId={} used={} earned={} current={} next={}",
                event.paymentTransactionId(),
                event.bookingId(),
                event.userId(),
                usedPoints,
                earnedPoints,
                currentPoints,
                nextPoints);
    }

    private void validateEvent(LoyaltyPointsSyncEvent event) {
        if (event == null
                || event.paymentTransactionId() == null
                || event.bookingId() == null
                || event.userId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (event.loyaltyPointsUsed() < 0 || event.loyaltyPointsEarned() < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private long normalize(long value) {
        return Math.max(value, 0L);
    }
}
