package com.cinema.user_service.messaging.consumer;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.messaging.LoyaltyPointsSyncEvent;
import com.cinema.messaging.LoyaltyPointsQueueNames;
import com.cinema.user_service.services.LoyaltyPointsSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyPointsSyncListener {

    private final LoyaltyPointsSyncService loyaltyPointsSyncService;

    @RabbitListener(queues = LoyaltyPointsQueueNames.QUEUE)
    public void handle(LoyaltyPointsSyncEvent event) {
        if (event == null
                || event.paymentTransactionId() == null
                || event.bookingId() == null
                || event.userId() == null
                || !StringUtils.hasText(event.source())
                || event.loyaltyPointsUsed() < 0
                || event.loyaltyPointsEarned() < 0) {
            throw new AmqpRejectAndDontRequeueException("Invalid loyalty points event payload");
        }

        try {
            loyaltyPointsSyncService.apply(event);
        } catch (DataIntegrityViolationException ex) {
            log.info(
                    "LOYALTY_SYNC_DUPLICATE_MESSAGE transactionId={} bookingId={} userId={} source={}",
                    event.paymentTransactionId(),
                    event.bookingId(),
                    event.userId(),
                    event.source());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.INVALID_INPUT || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new AmqpRejectAndDontRequeueException("Permanent loyalty sync failure: " + ex.getErrorCode().name(), ex);
            }
            throw ex;
        }
    }
}
