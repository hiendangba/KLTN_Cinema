package com.cinema.user_service.messaging.consumer;

import com.cinema.exception.BusinessException;
import com.cinema.messaging.CustomerRankSettlementEvent;
import com.cinema.messaging.CustomerRankSettlementQueueNames;
import com.cinema.user_service.services.CustomerRankSettlementSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerRankSettlementListener {

    private final CustomerRankSettlementSyncService settlementSyncService;

    @RabbitListener(queues = CustomerRankSettlementQueueNames.QUEUE)
    public void handle(CustomerRankSettlementEvent event) {
        try {
            settlementSyncService.apply(event);
        } catch (BusinessException ex) {
            log.warn("Customer rank settlement event rejected errorCode={} message={}",
                    ex.getErrorCode().name(),
                    ex.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid customer rank settlement event payload", ex);
        }
    }
}
