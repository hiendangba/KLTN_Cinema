package com.cinema.booking_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

@Embeddable
@Getter
@Setter
public class CustomerInfo {
    @Column(name = "customer_full_name", length = 120)
    private String fullName;

    @Column(name = "customer_email", length = 180)
    private String email;

    @Column(name = "customer_phone", length = 30)
    private String phone;
}

