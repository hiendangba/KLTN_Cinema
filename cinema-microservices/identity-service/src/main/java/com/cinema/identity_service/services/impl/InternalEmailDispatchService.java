package com.cinema.identity_service.services.impl;

import com.cinema.dto.request.SendEmailRequest;
import com.cinema.identity_service.grpc.EmailGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InternalEmailDispatchService {

    private final EmailGrpcClient emailGrpcClient;

    @Async
    public void sendAsync(SendEmailRequest request) {
        try {
            emailGrpcClient.sendEmail(request);
            log.info("Email dispatch accepted via gRPC: to={}, subject={}", request.getTo(), request.getSubject());
        } catch (Exception ex) {
            log.error("Failed to dispatch email via gRPC: to={}, subject={}", request.getTo(), request.getSubject(), ex);
        }
    }
}
