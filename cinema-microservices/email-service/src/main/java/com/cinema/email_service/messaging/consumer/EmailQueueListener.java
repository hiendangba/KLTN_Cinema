package com.cinema.email_service.messaging.consumer;

import com.cinema.dto.request.SendEmailRequest;
import com.cinema.email_service.services.EmailService;
import com.cinema.messaging.EmailQueueNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailQueueListener {

    private final EmailService emailService;

    @RabbitListener(
            queues = EmailQueueNames.QUEUE,
            containerFactory = "rabbitListenerContainerFactory",
            errorHandler = "emailListenerErrorHandler"
    )
    public void handleSendEmail(SendEmailRequest request) {
        log.info("Email job received from RabbitMQ: to={}, subject={}", request.getTo(), request.getSubject());
        emailService.sendEmail(request);
    }
}
