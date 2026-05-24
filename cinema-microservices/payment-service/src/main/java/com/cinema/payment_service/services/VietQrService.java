package com.cinema.payment_service.services;

import com.cinema.payment_service.dto.response.VietQrBankResponse;

import java.util.List;

public interface VietQrService {
    List<VietQrBankResponse> getBanks();

    int syncBanksMonthly();
}
