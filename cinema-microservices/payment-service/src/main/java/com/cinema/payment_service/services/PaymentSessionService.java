package com.cinema.payment_service.services;

import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.FilmRevenueReportRequest;
import com.cinema.payment_service.dto.request.PaymentSessionField;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueReportResponse;
import com.cinema.payment_service.dto.response.FilmRevenueReportResponse;
import com.cinema.payment_service.dto.response.PromotionSelectionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.momo.MomoIpnRequest;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.UUID;

public interface PaymentSessionService {
    ActionMessageResponse createSession(CreatePaymentSessionRequest request, UUID requesterUserId,
            String requesterRole);

    PaymentSessionResponse getSession(UUID bookingId, UUID requesterUserId, String requesterRole);

    ActionMessageResponse completeSession(UUID bookingId, UUID requesterUserId, String requesterRole);

    PageResponse<PaymentSessionResponse> searchMySessions(
            PageRequest<PaymentSessionField> request,
            UUID requesterUserId);

    ActionMessageResponse requestRefund(UUID bookingId, UUID requesterUserId, RefundPaymentRequest request);

    CinemaRevenueReportResponse getAllCinemaRevenueReport(CinemaRevenueReportRequest request);

    CinemaRevenueReportResponse getMyCinemaRevenueReport(CinemaRevenueReportRequest request, UUID requesterUserId);

    byte[] exportCinemaRevenueReport(CinemaRevenueReportRequest request, HttpServletRequest httpRequest);

    FilmRevenueReportResponse searchFilmRevenueReport(FilmRevenueReportRequest request, HttpServletRequest httpRequest);

    byte[] exportFilmRevenueReport(FilmRevenueReportRequest request, HttpServletRequest httpRequest);

    PromotionPreviewResponse previewPromotion(PromotionPreviewRequest request, UUID requesterUserId);

    PromotionSelectionResponse listSelectablePromotions(UUID bookingId, UUID requesterUserId);

    WebhookProcessingResult handleMomoReturn(MomoIpnRequest request);

    WebhookProcessingResult handleMomoWebhook(MomoIpnRequest request);

    void expireDueSessions();

    record WebhookProcessingResult(HttpStatus status, Map<String, Object> body) {
    }
}
