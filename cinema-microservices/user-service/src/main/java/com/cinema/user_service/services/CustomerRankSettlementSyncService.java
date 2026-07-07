package com.cinema.user_service.services;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.messaging.CustomerRankSettlementEvent;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.entity.UserRankSettlementLedger;
import com.cinema.user_service.repository.UserRankSettlementLedgerRepository;
import com.cinema.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerRankSettlementSyncService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);

    private final UserRepository userRepository;
    private final UserRankSettlementLedgerRepository ledgerRepository;

    @Transactional
    public void apply(CustomerRankSettlementEvent event) {
        validateEvent(event);
        String settlementType = normalizeType(event.settlementType());
        if (ledgerRepository.existsByPaymentTransactionIdAndSettlementType(
                event.paymentTransactionId(),
                settlementType)) {
            log.info(
                    "CUSTOMER_RANK_SETTLEMENT_DUPLICATE_SKIP transactionId={} bookingId={} userId={} type={}",
                    event.paymentTransactionId(),
                    event.bookingId(),
                    event.userId(),
                    settlementType);
            return;
        }

        User user = userRepository.findByIdAndIsDeletedFalse(event.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        BigDecimal currentAmount = normalizeAmount(user.getLifetimePaidAmount());
        BigDecimal nextAmount = currentAmount.add(normalizeAmount(event.settlementAmount()));
        if (nextAmount.compareTo(ZERO) < 0) {
            nextAmount = ZERO;
        }

        UserRankSettlementLedger ledger = new UserRankSettlementLedger();
        ledger.setPaymentTransactionId(event.paymentTransactionId());
        ledger.setBookingId(event.bookingId());
        ledger.setUserId(event.userId());
        ledger.setSettlementType(settlementType);
        ledger.setSettlementAmount(normalizeAmount(event.settlementAmount()));
        ledger.setLifetimeAmountBefore(currentAmount);
        ledger.setLifetimeAmountAfter(nextAmount);
        ledger.setSource(StringUtils.hasText(event.source()) ? event.source() : "unknown");
        ledger.setOccurredAt(event.occurredAt() == null ? LocalDateTime.now() : event.occurredAt());
        ledger.setProcessedAt(LocalDateTime.now());
        ledgerRepository.save(ledger);

        user.setLifetimePaidAmount(nextAmount);
        userRepository.save(user);
        log.info(
                "CUSTOMER_RANK_SETTLEMENT_APPLIED transactionId={} bookingId={} userId={} type={} amount={} current={} next={}",
                event.paymentTransactionId(),
                event.bookingId(),
                event.userId(),
                settlementType,
                event.settlementAmount(),
                currentAmount,
                nextAmount);
    }

    private void validateEvent(CustomerRankSettlementEvent event) {
        if (event == null
                || event.paymentTransactionId() == null
                || event.bookingId() == null
                || event.userId() == null
                || event.settlementAmount() == null
                || !StringUtils.hasText(event.settlementType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(0, RoundingMode.HALF_UP);
    }
}
