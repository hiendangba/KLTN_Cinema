package com.cinema.booking_service.http;

import com.cinema.booking_service.dto.response.PaymentSessionSnapshotResponse;
import com.cinema.dto.response.APIResponse;
import com.cinema.http.HeaderNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
@Slf4j
public class PaymentServiceClient {

    private static final ParameterizedTypeReference<APIResponse<PaymentSessionSnapshotResponse>> SESSION_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public PaymentServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${booking.payment-service-url:http://payment-service:8098/api/payments}") String paymentServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(paymentServiceUrl).build();
    }

    public PaymentSessionSnapshotResponse getSessionByBookingId(UUID bookingId, UUID requesterUserId) {
        try {
            APIResponse<PaymentSessionSnapshotResponse> response = restClient.get()
                    .uri("/sessions/{bookingId}", bookingId)
                    .header(HeaderNames.X_USER_ID, requesterUserId.toString())
                    .retrieve()
                    .body(SESSION_TYPE);
            return response == null ? null : response.getData();
        } catch (RestClientResponseException ex) {
            log.warn("Payment session fetch failed bookingId={} status={} message={}",
                    bookingId, ex.getStatusCode(), ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.warn("Payment session fetch failed bookingId={} reason={}", bookingId, ex.getMessage());
            return null;
        }
    }
}
