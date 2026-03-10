package com.cinema.email_service.services.impl;

import com.cinema.dto.request.SendEmailRequest;
import com.cinema.email_service.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender mailSender;
    @Value("${MAIL_USERNAME}")
    private String fromEmail;

    @Value("${MAIL_FROM_NAME}")
    private String fromName;

    @Override
    public void sendEmail(SendEmailRequest request) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail, fromName); // ⚠ BẮT BUỘC PHẢI CÓ
            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());
            helper.setText("<h1>" + request.getHeader() + "</h1></br><p>" + request.getContent() + "</p>", true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
