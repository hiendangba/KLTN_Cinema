package com.cinema.payment_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SePayTransactionPayload(
        @JsonProperty("id") String id,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("transaction_id") String transactionId,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("transaction_date") String transactionDate,
        @JsonProperty("transaction_status") String transactionStatus,
        @JsonProperty("transaction_amount") String transactionAmount,
        @JsonProperty("transaction_currency") String transactionCurrency) {
}

