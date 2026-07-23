package com.cinema.payment_service.scheduler;

import com.cinema.payment_service.services.PaymentSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionExpirationScheduler {

    private final PaymentSessionService paymentSessionService;

    @Scheduled(fixedDelayString = "${payment.expiration-scheduler-delay-ms:60000}")
    public void expireSessions() {
        try {
            paymentSessionService.expireDueSessions();
        } catch (Exception ex) {
            log.error("Unexpected error while expiring overdue payment sessions", ex);
        }
    }
}
