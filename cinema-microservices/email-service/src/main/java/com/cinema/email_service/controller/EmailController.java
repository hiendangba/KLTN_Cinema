package com.cinema.email_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.request.SendEmailRequest;
import com.cinema.email_service.services.EmailService;
import com.cinema.dto.response.APIResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
public class EmailController extends BaseController {
    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/send")
    public ResponseEntity<APIResponse<String>> sendEmail(@Valid @RequestBody SendEmailRequest request) {
        emailService.sendEmail(request);
        return ok("Gửi mail thành công!");
    }
}
