package com.cinema.payment_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SePayIpnRequest(
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("notification_type") String notificationType,
        @JsonProperty("order") SePayOrderPayload order,
        @JsonProperty("transaction") SePayTransactionPayload transaction,
        @JsonProperty("customer") SePayCustomerPayload customer) {
}

