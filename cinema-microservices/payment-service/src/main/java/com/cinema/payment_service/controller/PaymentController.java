package com.cinema.payment_service.controller;

import com.cinema.payment_service.dto.response.VietQrBanksApiResponse;
import com.cinema.payment_service.services.VietQrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final VietQrService vietQrService;

    public PaymentController(VietQrService vietQrService) {
        this.vietQrService = vietQrService;
    }

    @GetMapping("/vietqr/banks")
    public ResponseEntity<VietQrBanksApiResponse> getVietQrBanks() {
        return ResponseEntity.ok(vietQrService.getBanks());
    }
}
