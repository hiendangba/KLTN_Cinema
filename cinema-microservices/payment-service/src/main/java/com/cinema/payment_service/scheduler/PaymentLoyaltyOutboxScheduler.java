package com.cinema.payment_service.scheduler;

import com.cinema.payment_service.services.PaymentLoyaltyOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentLoyaltyOutboxScheduler {

    private final PaymentLoyaltyOutboxService paymentLoyaltyOutboxService;

    @Scheduled(fixedDelayString = "${payment.loyalty-outbox.publisher-delay-ms:15000}")
    public void publishPendingEvents() {
        try {
            int published = paymentLoyaltyOutboxService.publishDueEvents();
            if (published > 0) {
                log.info("LOYALTY_OUTBOX_PUBLISHER_TICK published={}", published);
            }
        } catch (Exception ex) {
            log.error("Unexpected error while publishing loyalty outbox events", ex);
        }
    }
}
