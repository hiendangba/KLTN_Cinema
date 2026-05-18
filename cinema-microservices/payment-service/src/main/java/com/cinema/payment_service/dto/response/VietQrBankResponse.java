package com.cinema.payment_service.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VietQrBankResponse {
    private Integer id;
    private String name;
    private String code;
    private String bin;
    private String shortName;
    private String logo;
    private Integer transferSupported;
    private Integer lookupSupported;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getBin() {
        return bin;
    }

    public void setBin(String bin) {
        this.bin = bin;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public Integer getTransferSupported() {
        return transferSupported;
    }

    public void setTransferSupported(Integer transferSupported) {
        this.transferSupported = transferSupported;
    }

    public Integer getLookupSupported() {
        return lookupSupported;
    }

    public void setLookupSupported(Integer lookupSupported) {
        this.lookupSupported = lookupSupported;
    }
}
