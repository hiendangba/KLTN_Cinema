package com.cinema.payment_service.services;

import com.cinema.payment_service.dto.response.VietQrBanksApiResponse;

public interface VietQrService {
    VietQrBanksApiResponse getBanks();

    int syncBanksMonthly();
}
