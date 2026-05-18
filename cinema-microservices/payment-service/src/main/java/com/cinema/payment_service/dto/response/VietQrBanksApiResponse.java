package com.cinema.payment_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VietQrBanksApiResponse {
    private String code;
    private String desc;
    private List<VietQrBankResponse> data;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public List<VietQrBankResponse> getData() {
        return data;
    }

    public void setData(List<VietQrBankResponse> data) {
        this.data = data;
    }
}
