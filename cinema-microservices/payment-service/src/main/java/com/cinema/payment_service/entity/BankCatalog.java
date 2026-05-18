package com.cinema.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_catalog")
public class BankCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vietqr_bank_id", nullable = false, unique = true)
    private Integer vietQrBankId;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, unique = true, length = 20)
    private String bin;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "short_name", nullable = false, length = 100)
    private String shortName;

    @Column(length = 500)
    private String logo;

    @Column(name = "transfer_supported", nullable = false)
    private Integer transferSupported;

    @Column(name = "lookup_supported", nullable = false)
    private Integer lookupSupported;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVietQrBankId() {
        return vietQrBankId;
    }

    public void setVietQrBankId(Integer vietQrBankId) {
        this.vietQrBankId = vietQrBankId;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
