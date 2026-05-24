package com.cinema.payment_service.controller;

import com.cinema.payment_service.dto.response.VietQrBankResponse;
import com.cinema.payment_service.services.VietQrService;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController extends BaseController {

    private final VietQrService vietQrService;

    public PaymentController(VietQrService vietQrService) {
        this.vietQrService = vietQrService;
    }

    @GetMapping("/vietqr/banks")
    public ResponseEntity<APIResponse<java.util.List<VietQrBankResponse>>> getVietQrBanks() {
        return ok(vietQrService.getBanks());
    }
}
