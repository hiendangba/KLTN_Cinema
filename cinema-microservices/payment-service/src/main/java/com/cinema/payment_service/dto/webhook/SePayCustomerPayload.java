package com.cinema.payment_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SePayCustomerPayload(
        @JsonProperty("id") String id,
        @JsonProperty("customer_id") String customerId) {
}
