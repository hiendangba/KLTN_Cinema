package com.cinema.payment_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.FilmRevenueReportRequest;
import com.cinema.payment_service.dto.request.PaymentSessionField;
import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.request.RefundPaymentRequest;
import com.cinema.payment_service.dto.momo.MomoIpnRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueReportResponse;
import com.cinema.payment_service.dto.response.FilmRevenueReportResponse;
import com.cinema.payment_service.dto.response.PromotionSelectionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.dto.response.VietQrBankResponse;
import com.cinema.payment_service.services.PaymentSessionService;
import com.cinema.payment_service.services.VietQrService;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.excel.ExcelExportUtils;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController extends BaseController {

    private final VietQrService vietQrService;
    private final PaymentSessionService paymentSessionService;

    @GetMapping("/vietqr/banks")
    public ResponseEntity<APIResponse<java.util.List<VietQrBankResponse>>> getVietQrBanks() {
        return ok(SuccessMessage.VIETQR_BANKS_FETCHED, vietQrService.getBanks());
    }

    @PostMapping("/sessions")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createSession(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreatePaymentSessionRequest request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        String requesterRole = RequestAuthUtils.requireRoleHeader(servletRequest);
        return created(SuccessMessage.PAYMENT_SESSION_CREATED,
                paymentSessionService.createSession(request, requesterUserId, requesterRole));
    }

    @GetMapping("/sessions/{bookingId}")
    public ResponseEntity<APIResponse<PaymentSessionResponse>> getSession(
            HttpServletRequest servletRequest,
            @PathVariable UUID bookingId) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        String requesterRole = RequestAuthUtils.requireRoleHeader(servletRequest);
        return ok(SuccessMessage.PAYMENT_SESSION_FETCHED,
                paymentSessionService.getSession(bookingId, requesterUserId, requesterRole));
    }

    @PostMapping("/sessions/{bookingId}/complete")
    public ResponseEntity<APIResponse<ActionMessageResponse>> completeSession(
            HttpServletRequest servletRequest,
            @PathVariable UUID bookingId) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        RequestAuthUtils.requireAnyRole(servletRequest, HeaderNames.ROLE_STAFF, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_ADMIN);
        String requesterRole = RequestAuthUtils.requireRoleHeader(servletRequest);
        return ok(SuccessMessage.PAYMENT_SESSION_COMPLETED,
                paymentSessionService.completeSession(bookingId, requesterUserId, requesterRole));
    }

    @PostMapping("/me/sessions/search")
    public ResponseEntity<APIResponse<PageResponse<PaymentSessionResponse>>> searchMySessions(
            HttpServletRequest servletRequest,
            @Valid @RequestBody PageRequest<PaymentSessionField> request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(SuccessMessage.PAYMENT_SESSIONS_SEARCHED,
                paymentSessionService.searchMySessions(request, requesterUserId));
    }

    @PostMapping("/sessions/{bookingId}/refund")
    public ResponseEntity<APIResponse<ActionMessageResponse>> requestRefund(
            HttpServletRequest servletRequest,
            @PathVariable UUID bookingId,
            @RequestBody(required = false) RefundPaymentRequest request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(SuccessMessage.PAYMENT_REFUND_REQUESTED,
                paymentSessionService.requestRefund(bookingId, requesterUserId, request));
    }

    @PostMapping("/revenues/cinemas/search")
    public ResponseEntity<APIResponse<CinemaRevenueReportResponse>> getAllCinemaRevenueReport(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CinemaRevenueReportRequest request) {
        RequestAuthUtils.requireRole(servletRequest, HeaderNames.ROLE_ADMIN);
        return ok(SuccessMessage.CINEMA_REVENUE_REPORT_FETCHED, paymentSessionService.getAllCinemaRevenueReport(request));
    }

    @PostMapping("/revenues/cinemas/me/search")
    public ResponseEntity<APIResponse<CinemaRevenueReportResponse>> getMyCinemaRevenueReport(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CinemaRevenueReportRequest request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        RequestAuthUtils.requireRole(servletRequest, HeaderNames.ROLE_MANAGER);
        return ok(SuccessMessage.CINEMA_REVENUE_REPORT_FETCHED,
                paymentSessionService.getMyCinemaRevenueReport(request, requesterUserId));
    }

    @PostMapping("/revenues/cinemas/export")
    public ResponseEntity<byte[]> exportCinemaRevenueReport(
            HttpServletRequest servletRequest,
            @Valid @RequestBody CinemaRevenueReportRequest request) {
        RequestAuthUtils.requireAnyRole(servletRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        byte[] file = paymentSessionService.exportCinemaRevenueReport(request, servletRequest);
        return ExcelExportUtils.buildDownloadResponse(file, "payment_revenue_report.xlsx");
    }

    @PostMapping("/revenues/films/search")
    public ResponseEntity<APIResponse<FilmRevenueReportResponse>> searchFilmRevenueReport(
            HttpServletRequest servletRequest,
            @Valid @RequestBody FilmRevenueReportRequest request) {
        RequestAuthUtils.requireAnyRole(servletRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
        return ok(SuccessMessage.CINEMA_REVENUE_REPORT_FETCHED,
                paymentSessionService.searchFilmRevenueReport(request, servletRequest));
    }

    @PostMapping("/revenues/films/export")
    public ResponseEntity<byte[]> exportFilmRevenueReport(
            HttpServletRequest servletRequest,
            @Valid @RequestBody FilmRevenueReportRequest request) {
        RequestAuthUtils.requireAnyRole(servletRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
        byte[] file = paymentSessionService.exportFilmRevenueReport(request, servletRequest);
        return ExcelExportUtils.buildDownloadResponse(file, "payment_film_revenue_report.xlsx");
    }

    @PostMapping("/promotions/preview")
    public ResponseEntity<APIResponse<PromotionPreviewResponse>> previewPromotion(
            HttpServletRequest servletRequest,
            @Valid @RequestBody PromotionPreviewRequest request) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(SuccessMessage.PROMOTION_PREVIEW_FETCHED,
                paymentSessionService.previewPromotion(request, requesterUserId));
    }

    @GetMapping("/bookings/{bookingId}/promotions")
    public ResponseEntity<APIResponse<PromotionSelectionResponse>> listSelectablePromotions(
            HttpServletRequest servletRequest,
            @PathVariable UUID bookingId) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(servletRequest);
        return ok(SuccessMessage.PROMOTION_OPTIONS_FETCHED,
                paymentSessionService.listSelectablePromotions(bookingId, requesterUserId));
    }

    @PostMapping("/webhooks/momo")
    public ResponseEntity<?> handleMomoWebhook(
            @RequestBody(required = false) MomoIpnRequest request) {
        PaymentSessionService.WebhookProcessingResult result = paymentSessionService.handleMomoWebhook(request);
        log.info(
                "MOMO_IPN_HTTP_RESPONSE orderId={} requestId={} resultCode={} status={}",
                request == null || request.orderId() == null ? "" : request.orderId(),
                request == null || request.requestId() == null ? "" : request.requestId(),
                request == null || request.resultCode() == null ? "" : request.resultCode(),
                result.status().value());
        if (result.status().is2xxSuccessful() && result.status().value() == 204) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
