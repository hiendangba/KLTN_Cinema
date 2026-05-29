package com.cinema.payment_service.services.impl;

import com.cinema.payment_service.config.MomoGatewayProperties;
import com.cinema.payment_service.dto.request.CinemaRevenueField;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueReportResponse;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import com.cinema.payment_service.repository.PaymentTransactionRepository;
import com.cinema.payment_service.repository.PaymentTransactionRepositoryImpl;
import com.cinema.payment_service.repository.PaymentTransactionPromotionRepository;
import com.cinema.payment_service.support.MomoPaymentGatewayClient;
import com.cinema.payment_service.support.PromotionEngine;
import com.cinema.payment_service.support.PromotionQuote;
import com.cinema.dto.request.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSessionServiceImplTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentTransactionRepositoryImpl paymentTransactionRepositoryImpl;

    @Mock
    private PaymentTransactionPromotionRepository paymentTransactionPromotionRepository;

    @Mock
    private BookingGrpcClient bookingGrpcClient;

    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @Mock
    private MomoGatewayProperties momoGatewayProperties;

    @Mock
    private MomoPaymentGatewayClient momoPaymentGatewayClient;

    @Mock
    private PromotionEngine promotionEngine;

    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @InjectMocks
    private PaymentSessionServiceImpl paymentSessionService;

    @Test
    void createSession_shouldPersistFilmIdSnapshotFromBookingContext() {
        UUID bookingId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        BookingGrpcClient.BookingPaymentContext bookingContext = new BookingGrpcClient.BookingPaymentContext(
                bookingId,
                showtimeId,
                cinemaId,
                filmId,
                userId,
                BigDecimal.valueOf(180000),
                LocalDateTime.now().plusMinutes(30),
                "PENDING",
                "UNPAID",
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(30000),
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(180000));

        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(momoPaymentGatewayClient.createCheckout(any(PaymentTransaction.class), any()))
                .thenReturn(new MomoPaymentGatewayClient.MomoCheckoutResult(
                        "https://momo.example.com/pay",
                        "https://momo.example.com/qr",
                        "{}",
                        "{\"resultCode\":0,\"payUrl\":\"https://momo.example.com/pay\"}"));

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);

        PaymentSessionResponse response = paymentSessionService.createSession(request, userId);

        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(transactionCaptor.capture());

        PaymentTransaction saved = transactionCaptor.getValue();
        assertNotNull(response);
        assertEquals(bookingId, response.getBookingId());
        assertEquals(filmId, saved.getFilmId());
        assertEquals("https://momo.example.com/pay", response.getPayUrl());
        assertEquals("https://momo.example.com/pay", saved.getPayUrl());
        verify(bookingGrpcClient).upsertBookingPromotionSnapshot(
                bookingId,
                null,
                null,
                null,
                BigDecimal.ZERO.setScale(0),
                BigDecimal.valueOf(180000).setScale(0));
    }

    @Test
    void createSession_shouldApplyPromotionSnapshotWhenCodeIsProvided() {
        UUID bookingId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        BookingGrpcClient.BookingPaymentContext bookingContext = new BookingGrpcClient.BookingPaymentContext(
                bookingId,
                showtimeId,
                cinemaId,
                filmId,
                userId,
                BigDecimal.valueOf(180000),
                LocalDateTime.now().plusMinutes(30),
                "PENDING",
                "UNPAID",
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(30000),
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(180000));

        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(momoPaymentGatewayClient.createCheckout(any(PaymentTransaction.class), any()))
                .thenReturn(new MomoPaymentGatewayClient.MomoCheckoutResult(
                        "https://momo.example.com/pay",
                        "https://momo.example.com/qr",
                        "{}",
                        "{\"resultCode\":0,\"payUrl\":\"https://momo.example.com/pay\"}"));
        UUID promotionId = UUID.randomUUID();
        when(promotionEngine.resolvePromotionForCheckout(
                "CINEMASTAR10",
                BigDecimal.valueOf(180000),
                bookingContext,
                userId))
                .thenReturn(new PromotionQuote(
                        "CINEMASTAR10",
                        "CinemaStar 10%",
                        BigDecimal.valueOf(18000),
                        "Applied 10% discount",
                        promotionId));

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);
        request.setPromotionCode("CINEMASTAR10");

        PaymentSessionResponse response = paymentSessionService.createSession(request, userId);

        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(transactionCaptor.capture());

        PaymentTransaction saved = transactionCaptor.getValue();
        assertNotNull(response);
        assertEquals(bookingId, response.getBookingId());
        assertEquals("CINEMASTAR10", saved.getPromotionCode());
        assertEquals(BigDecimal.valueOf(18000), saved.getPromotionDiscountAmount());
        assertEquals(BigDecimal.valueOf(162000), saved.getAmount());
        assertEquals(BigDecimal.valueOf(162000), response.getAmount());
        verify(bookingGrpcClient).upsertBookingPromotionSnapshot(
                bookingId,
                promotionId,
                "CINEMASTAR10",
                "CinemaStar 10%",
                BigDecimal.valueOf(18000).setScale(0),
                BigDecimal.valueOf(162000).setScale(0));
    }

    @Test
    void getAllCinemaRevenueReport_shouldFilterByCinemaAndFilmBeforeAggregating() {
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"),
                new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2")));

        PaymentTransaction tx1 = buildTransaction(cinema1, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 9, 0), null,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.valueOf(10000),
                "CINEMASTAR10", "CinemaStar 10%");
        PaymentTransaction tx2 = buildTransaction(cinema1, film2, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 10, 0), null,
                BigDecimal.valueOf(120000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
        PaymentTransaction tx3 = buildTransaction(cinema2, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 11, 0), null,
                BigDecimal.valueOf(130000), BigDecimal.ZERO, BigDecimal.valueOf(5000),
                "WEEKDAY15", "Weekday 15%");

        when(paymentTransactionRepositoryImpl.findAllForRevenueReport(
                anyCollection(),
                anyCollection(),
                any(),
                any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<UUID> cinemaIds = (List<UUID>) invocation.getArgument(0);
                    @SuppressWarnings("unchecked")
                    List<UUID> filmIds = (List<UUID>) invocation.getArgument(1);
                    LocalDateTime from = invocation.getArgument(2);
                    LocalDateTime to = invocation.getArgument(3);
                    return List.of(tx1, tx2, tx3).stream()
                            .filter(tx -> cinemaIds.contains(tx.getCinemaId()))
                            .filter(tx -> filmIds == null || filmIds.isEmpty() || filmIds.contains(tx.getFilmId()))
                            .filter(tx -> tx.getPaidAt() != null
                                    && (from == null || !tx.getPaidAt().isBefore(from))
                                    && (to == null || !tx.getPaidAt().isAfter(to)))
                            .toList();
                });

        PageRequest<CinemaRevenueField> pageRequest = new PageRequest<>();
        pageRequest.setPage(1);
        pageRequest.setSize(10);

        CinemaRevenueReportRequest request = CinemaRevenueReportRequest.builder()
                .cinemaIds(List.of(cinema1))
                .filmIds(List.of(film1))
                .pageRequest(pageRequest)
                .build();

        CinemaRevenueReportResponse response = paymentSessionService.getAllCinemaRevenueReport(request);

        ArgumentCaptor<List<UUID>> cinemaIdsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<UUID>> filmIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paymentTransactionRepositoryImpl).findAllForRevenueReport(
                cinemaIdsCaptor.capture(),
                filmIdsCaptor.capture(),
                any(),
                any());

        assertEquals(List.of(cinema1), cinemaIdsCaptor.getValue());
        assertEquals(List.of(film1), filmIdsCaptor.getValue());
        assertEquals(1L, response.total().totalTransactions());
        assertEquals(1, response.items().size());
        assertEquals(cinema1, response.items().get(0).cinemaId());
        assertEquals(1L, response.items().get(0).totalTransactions());
        assertEquals("CINEMASTAR10", response.items().get(0).promotionCode());
        assertEquals("CinemaStar 10%", response.items().get(0).promotionName());
        assertEquals(BigDecimal.valueOf(10000), response.items().get(0).promotionDiscountAmount());
        assertEquals("CINEMASTAR10", response.total().promotionCode());
        assertEquals("CinemaStar 10%", response.total().promotionName());
        assertEquals(BigDecimal.valueOf(10000), response.total().promotionDiscountAmount());
    }

    @Test
    void getAllCinemaRevenueReport_shouldKeepPromotionDiscountZeroWhenNotApplied() {
        UUID cinema1 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));

        PaymentTransaction tx1 = buildTransaction(cinema1, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 9, 0), null,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);

        when(paymentTransactionRepositoryImpl.findAllForRevenueReport(
                anyCollection(),
                isNull(),
                any(),
                any()))
                .thenReturn(List.of(tx1));

        PageRequest<CinemaRevenueField> pageRequest = new PageRequest<>();
        pageRequest.setPage(1);
        pageRequest.setSize(10);

        CinemaRevenueReportRequest request = CinemaRevenueReportRequest.builder()
                .pageRequest(pageRequest)
                .build();

        CinemaRevenueReportResponse response = paymentSessionService.getAllCinemaRevenueReport(request);

        assertEquals(BigDecimal.ZERO.setScale(0), response.items().get(0).promotionDiscountAmount());
        assertEquals("", response.items().get(0).promotionCode());
        assertEquals("", response.items().get(0).promotionName());
        assertEquals(BigDecimal.ZERO.setScale(0), response.total().promotionDiscountAmount());
        assertEquals("", response.total().promotionCode());
        assertEquals("", response.total().promotionName());
    }

    private PaymentTransaction buildTransaction(
            UUID cinemaId,
            UUID filmId,
            PaymentTransactionStatus status,
            LocalDateTime paidAt,
            LocalDateTime refundedAt,
            BigDecimal amount,
            BigDecimal refundAmount,
            BigDecimal promotionDiscountAmount,
            String promotionCode,
            String promotionName) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setBookingId(UUID.randomUUID());
        transaction.setShowtimeId(UUID.randomUUID());
        transaction.setCinemaId(cinemaId);
        transaction.setFilmId(filmId);
        transaction.setUserId(UUID.randomUUID());
        transaction.setAmount(amount);
        transaction.setTicketSubtotalSnapshot(amount);
        transaction.setProductSubtotalSnapshot(BigDecimal.ZERO);
        transaction.setCurrency("VND");
        transaction.setPaymentMethod("MOMO_QR");
        transaction.setOrderInvoiceNumber("INV-" + UUID.randomUUID());
        transaction.setPayUrl("https://momo.example.com/pay");
        transaction.setCheckoutPayloadJson("{}");
        transaction.setStatus(status);
        transaction.setExpiresAt(LocalDateTime.now().plusHours(1));
        transaction.setPaidAt(paidAt);
        transaction.setRefundedAt(refundedAt);
        transaction.setRefundAmount(refundAmount);
        transaction.setPromotionCode(promotionCode);
        transaction.setPromotionName(promotionName);
        transaction.setPromotionDiscountAmount(promotionDiscountAmount);
        return transaction;
    }
}
