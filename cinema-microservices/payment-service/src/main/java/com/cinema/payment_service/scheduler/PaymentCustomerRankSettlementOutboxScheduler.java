package com.cinema.payment_service.scheduler;

import com.cinema.payment_service.services.PaymentCustomerRankSettlementOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCustomerRankSettlementOutboxScheduler {

    private final PaymentCustomerRankSettlementOutboxService outboxService;

    @Scheduled(fixedDelayString = "${payment.customer-rank-outbox.publisher-delay-ms:15000}")
    public void publishPendingEvents() {
        try {
            int published = outboxService.publishDueEvents();
            if (published > 0) {
                log.info("CUSTOMER_RANK_OUTBOX_PUBLISHER_TICK published={}", published);
            }
        } catch (Exception ex) {
            log.error("Unexpected error while publishing customer rank settlement outbox events", ex);
        }
    }
}
