package com.cinema.user_service.messaging.publisher;

import com.cinema.dto.request.SendEmailRequest;
import com.cinema.messaging.EmailQueueNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InternalEmailDispatchService {

    private final RabbitTemplate rabbitTemplate;

    @Async
    public void sendAsync(SendEmailRequest request) {
        try {
            rabbitTemplate.convertAndSend(
                    EmailQueueNames.EXCHANGE,
                    EmailQueueNames.ROUTING_KEY,
                    request
            );
            log.info("Audit email queued via RabbitMQ: to={}, subject={}", request.getTo(), request.getSubject());
        } catch (Exception ex) {
            log.error("Failed to queue audit email via RabbitMQ: to={}, subject={}", request.getTo(), request.getSubject(), ex);
        }
    }
}
