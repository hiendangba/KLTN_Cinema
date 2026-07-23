package com.cinema.payment_service.entity;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@Setter
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
}
