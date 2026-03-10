package com.cinema.email_service.services;

import com.cinema.dto.request.SendEmailRequest;

public interface EmailService {
    void sendEmail(SendEmailRequest request);
}
