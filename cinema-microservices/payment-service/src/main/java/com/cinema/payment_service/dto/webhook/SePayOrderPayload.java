package com.cinema.payment_service.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SePayOrderPayload(
        @JsonProperty("id") String id,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("order_status") String orderStatus,
        @JsonProperty("order_currency") String orderCurrency,
        @JsonProperty("order_amount") String orderAmount,
        @JsonProperty("order_invoice_number") String orderInvoiceNumber,
        @JsonProperty("order_description") String orderDescription) {
}

