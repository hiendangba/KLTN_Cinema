package com.cinema.payment_service.dto.response;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VietQrBanksApiResponse {
    private String code;
    private String desc;
    private List<VietQrBankResponse> data;
}
