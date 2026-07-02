package com.cinema.payment_service.services.impl;

import com.cinema.payment_service.config.MomoGatewayProperties;
import com.cinema.payment_service.dto.request.CinemaRevenueField;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.CreatePaymentSessionRequest;
import com.cinema.payment_service.dto.request.FilmRevenueField;
import com.cinema.payment_service.dto.request.FilmRevenueReportRequest;
import com.cinema.payment_service.dto.momo.MomoIpnRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueReportResponse;
import com.cinema.payment_service.dto.response.FilmRevenueReportResponse;
import com.cinema.Enum.SuccessMessage;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.payment_service.dto.response.PaymentSessionResponse;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.enums.PaymentTransactionStatus;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import com.cinema.payment_service.grpc.FilmGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PaymentTransactionRepository;
import com.cinema.payment_service.repository.PaymentTransactionRepositoryImpl;
import com.cinema.payment_service.repository.PaymentTransactionPromotionRepository;
import com.cinema.payment_service.services.PaymentSessionService;
import com.cinema.payment_service.support.MomoPaymentGatewayClient;
import com.cinema.payment_service.support.PromotionEngine;
import com.cinema.payment_service.support.PromotionQuote;
import com.cinema.payment_service.support.RevenueReportSupport;
import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.http.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
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
    private FilmGrpcClient filmGrpcClient;

    @Mock
    private MomoGatewayProperties momoGatewayProperties;

    @Mock
    private MomoPaymentGatewayClient momoPaymentGatewayClient;

    @Mock
    private PromotionEngine promotionEngine;

    @Spy
    private PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

    @Spy
    private RevenueReportSupport revenueReportSupport = new RevenueReportSupport();

    @Mock
    private HttpServletRequest httpRequest;

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
        stubSuccessfulCheckout();

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);

        ActionMessageResponse response = paymentSessionService.createSession(request, userId, HeaderNames.ROLE_CUSTOMER);

        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        verify(paymentTransactionRepository).saveAndFlush(any(PaymentTransaction.class));
        InOrder inOrder = inOrder(paymentTransactionRepository, momoPaymentGatewayClient);
        inOrder.verify(paymentTransactionRepository).saveAndFlush(any(PaymentTransaction.class));
        inOrder.verify(momoPaymentGatewayClient).createCheckout(any(PaymentTransaction.class), any());
        inOrder.verify(paymentTransactionRepository).save(any(PaymentTransaction.class));

        PaymentTransaction saved = transactionCaptor.getValue();
        assertNotNull(response);
        assertEquals(SuccessMessage.PAYMENT_SESSION_CREATED.getMessage(), response.getMessage());
        assertEquals(filmId, saved.getFilmId());
        assertEquals("https://momo.example.com/pay", saved.getPayUrl());
        assertEquals("{\"resultCode\":0,\"payUrl\":\"https://momo.example.com/pay\"}",
                saved.getResponsePayloadJson());
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
        stubSuccessfulCheckout();
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

        ActionMessageResponse response = paymentSessionService.createSession(request, userId, HeaderNames.ROLE_CUSTOMER);

        ArgumentCaptor<PaymentTransaction> transactionCaptor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(transactionCaptor.capture());
        verify(paymentTransactionRepository).saveAndFlush(any(PaymentTransaction.class));
        InOrder inOrder = inOrder(paymentTransactionRepository, momoPaymentGatewayClient);
        inOrder.verify(paymentTransactionRepository).saveAndFlush(any(PaymentTransaction.class));
        inOrder.verify(momoPaymentGatewayClient).createCheckout(any(PaymentTransaction.class), any());
        inOrder.verify(paymentTransactionRepository).save(any(PaymentTransaction.class));

        PaymentTransaction saved = transactionCaptor.getValue();
        assertNotNull(response);
        assertEquals(SuccessMessage.PAYMENT_SESSION_CREATED.getMessage(), response.getMessage());
        assertEquals("CINEMASTAR10", saved.getPromotionCode());
        assertEquals(BigDecimal.valueOf(18000), saved.getPromotionDiscountAmount());
        assertEquals(BigDecimal.valueOf(162000), saved.getAmount());
        assertEquals("{\"resultCode\":0,\"payUrl\":\"https://momo.example.com/pay\"}",
                saved.getResponsePayloadJson());
        verify(bookingGrpcClient).upsertBookingPromotionSnapshot(
                bookingId,
                promotionId,
                "CINEMASTAR10",
                "CinemaStar 10%",
                BigDecimal.valueOf(18000).setScale(0),
                BigDecimal.valueOf(162000).setScale(0));
    }

    @Test
    void createSession_shouldRejectCustomerForAnotherUserBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        BookingGrpcClient.BookingPaymentContext bookingContext = buildBookingContext(
                bookingId,
                cinemaId,
                ownerId,
                "PENDING",
                "UNPAID");
        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentSessionService.createSession(request, requesterId, HeaderNames.ROLE_CUSTOMER));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void createSession_shouldAllowStaffWithinAssignedCinema() {
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        BookingGrpcClient.BookingPaymentContext bookingContext = buildBookingContext(
                bookingId,
                cinemaId,
                ownerId,
                "PENDING",
                "UNPAID");
        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(buildReusableTransaction(bookingId, UUID.randomUUID())));
        when(cinemaGrpcClient.getCinemasByUserId(requesterId, HeaderNames.ROLE_STAFF))
                .thenReturn(List.of(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema 1")));

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);

        ActionMessageResponse response = paymentSessionService.createSession(request, requesterId, HeaderNames.ROLE_STAFF);

        assertEquals(SuccessMessage.PAYMENT_SESSION_CREATED.getMessage(), response.getMessage());
        verify(paymentTransactionRepository).findFirstByBookingIdOrderByTimeCreatedDesc(bookingId);
        verify(paymentTransactionRepository, times(0)).saveAndFlush(any(PaymentTransaction.class));
        verifyNoInteractions(momoPaymentGatewayClient);
    }

    @Test
    void createSession_shouldRejectStaffOutsideAssignedCinema() {
        UUID bookingId = UUID.randomUUID();
        UUID bookingCinemaId = UUID.randomUUID();
        UUID assignedCinemaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        BookingGrpcClient.BookingPaymentContext bookingContext = buildBookingContext(
                bookingId,
                bookingCinemaId,
                ownerId,
                "PENDING",
                "UNPAID");
        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(cinemaGrpcClient.getCinemasByUserId(requesterId, HeaderNames.ROLE_STAFF))
                .thenReturn(List.of(new CinemaGrpcClient.CinemaSummary(assignedCinemaId, "Cinema 2")));

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentSessionService.createSession(request, requesterId, HeaderNames.ROLE_STAFF));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void createSession_shouldAllowManagerWithinManagedCinema() {
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        BookingGrpcClient.BookingPaymentContext bookingContext = buildBookingContext(
                bookingId,
                cinemaId,
                ownerId,
                "PENDING",
                "UNPAID");
        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(buildReusableTransaction(bookingId, UUID.randomUUID())));
        when(cinemaGrpcClient.getCinemasByUserId(requesterId, HeaderNames.ROLE_MANAGER))
                .thenReturn(List.of(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema 1")));

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);

        ActionMessageResponse response = paymentSessionService.createSession(request, requesterId, HeaderNames.ROLE_MANAGER);

        assertEquals(SuccessMessage.PAYMENT_SESSION_CREATED.getMessage(), response.getMessage());
        verify(paymentTransactionRepository).findFirstByBookingIdOrderByTimeCreatedDesc(bookingId);
        verify(paymentTransactionRepository, times(0)).saveAndFlush(any(PaymentTransaction.class));
        verifyNoInteractions(momoPaymentGatewayClient);
    }

    @Test
    void createSession_shouldAllowAdminAcrossCinemaScope() {
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        BookingGrpcClient.BookingPaymentContext bookingContext = buildBookingContext(
                bookingId,
                cinemaId,
                ownerId,
                "PENDING",
                "UNPAID");
        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(buildReusableTransaction(bookingId, UUID.randomUUID())));

        CreatePaymentSessionRequest request = new CreatePaymentSessionRequest();
        request.setBookingId(bookingId);

        ActionMessageResponse response = paymentSessionService.createSession(request, requesterId, HeaderNames.ROLE_ADMIN);

        assertEquals(SuccessMessage.PAYMENT_SESSION_CREATED.getMessage(), response.getMessage());
        verify(paymentTransactionRepository).findFirstByBookingIdOrderByTimeCreatedDesc(bookingId);
        verify(paymentTransactionRepository, times(0)).saveAndFlush(any(PaymentTransaction.class));
        verifyNoInteractions(cinemaGrpcClient, momoPaymentGatewayClient);
    }

    @Test
    void getSession_shouldAllowStaffWithinAssignedCinemaAndReturnQrCodeUrl() {
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        PaymentTransaction transaction = buildReusableTransaction(bookingId, ownerId);
        transaction.setCinemaId(cinemaId);
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setResponsePayloadJson("{\"qrCodeUrl\":\"https://momo.example.com/qr\"}");

        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(transaction));
        when(cinemaGrpcClient.getCinemasByUserId(requesterId, HeaderNames.ROLE_STAFF))
                .thenReturn(List.of(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema 1")));

        PaymentSessionResponse response = paymentSessionService.getSession(bookingId, requesterId, HeaderNames.ROLE_STAFF);

        assertNotNull(response);
        assertEquals(bookingId, response.getBookingId());
        assertEquals("https://momo.example.com/qr", response.getQrCodeUrl());
        assertNull(response.getCompletedByUserId());
        assertNull(response.getCompletedByRole());
        verifyNoInteractions(bookingGrpcClient);
    }

    @Test
    void completeSession_shouldMarkPaidAndStoreAuditForStaff() {
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        PaymentTransaction transaction = buildPendingTransaction("PAY-" + UUID.randomUUID(), BigDecimal.valueOf(180000));
        transaction.setBookingId(bookingId);
        transaction.setCinemaId(cinemaId);
        transaction.setUserId(ownerId);
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setProviderRef("OLD_PROVIDER");

        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cinemaGrpcClient.getCinemasByUserId(requesterId, HeaderNames.ROLE_STAFF))
                .thenReturn(List.of(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema 1")));

        ActionMessageResponse response = paymentSessionService.completeSession(bookingId, requesterId, HeaderNames.ROLE_STAFF);

        assertEquals(SuccessMessage.PAYMENT_SESSION_COMPLETED.getMessage(), response.getMessage());
        assertEquals(PaymentTransactionStatus.PAID, transaction.getStatus());
        assertNotNull(transaction.getPaidAt());
        assertNull(transaction.getProviderRef());
        assertNull(transaction.getFailureReason());
        assertEquals(requesterId, transaction.getCompletedByUserId());
        assertEquals(HeaderNames.ROLE_STAFF, transaction.getCompletedByRole());
        verify(bookingGrpcClient).confirmBookingPayment(
                bookingId,
                transaction.getAmount(),
                transaction.getPaymentMethod(),
                null,
                transaction.getOrderInvoiceNumber());
    }

    @Test
    void completeSession_shouldAllowAdminAcrossCinemaScope() {
        UUID bookingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        PaymentTransaction transaction = buildPendingTransaction("PAY-" + UUID.randomUUID(), BigDecimal.valueOf(180000));
        transaction.setBookingId(bookingId);
        transaction.setCinemaId(UUID.randomUUID());

        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = paymentSessionService.completeSession(bookingId, requesterId, HeaderNames.ROLE_ADMIN);

        assertEquals(SuccessMessage.PAYMENT_SESSION_COMPLETED.getMessage(), response.getMessage());
        assertEquals(requesterId, transaction.getCompletedByUserId());
        assertEquals(HeaderNames.ROLE_ADMIN, transaction.getCompletedByRole());
        verifyNoInteractions(cinemaGrpcClient);
        verify(bookingGrpcClient).confirmBookingPayment(
                bookingId,
                transaction.getAmount(),
                transaction.getPaymentMethod(),
                null,
                transaction.getOrderInvoiceNumber());
    }

    @Test
    void completeSession_shouldRejectCustomerRole() {
        UUID bookingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        PaymentTransaction transaction = buildPendingTransaction("PAY-" + UUID.randomUUID(), BigDecimal.valueOf(180000));
        transaction.setBookingId(bookingId);
        transaction.setCinemaId(UUID.randomUUID());

        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(transaction));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentSessionService.completeSession(bookingId, requesterId, HeaderNames.ROLE_CUSTOMER));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(bookingGrpcClient, cinemaGrpcClient);
    }

    @Test
    void completeSession_shouldBeIdempotentWhenAlreadyPaid() {
        UUID bookingId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        PaymentTransaction transaction = buildPendingTransaction("PAY-" + UUID.randomUUID(), BigDecimal.valueOf(180000));
        transaction.setBookingId(bookingId);
        transaction.setCinemaId(UUID.randomUUID());
        transaction.setStatus(PaymentTransactionStatus.PAID);
        transaction.setCompletedByUserId(UUID.randomUUID());
        transaction.setCompletedByRole(HeaderNames.ROLE_ADMIN);

        when(paymentTransactionRepository.findFirstByBookingIdOrderByTimeCreatedDesc(bookingId))
                .thenReturn(Optional.of(transaction));

        ActionMessageResponse response = paymentSessionService.completeSession(bookingId, requesterId, HeaderNames.ROLE_ADMIN);

        assertEquals(SuccessMessage.PAYMENT_SESSION_COMPLETED.getMessage(), response.getMessage());
        verify(paymentTransactionRepository, times(0)).save(any(PaymentTransaction.class));
        verifyNoInteractions(bookingGrpcClient, cinemaGrpcClient);
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

    @Test
    void getAllCinemaRevenueReport_shouldAllowOnlyFromDate() {
        UUID cinema1 = UUID.randomUUID();
        LocalDateTime from = LocalDateTime.of(2026, 5, 29, 9, 30);

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
        when(paymentTransactionRepositoryImpl.findAllForRevenueReport(
                anyCollection(),
                isNull(),
                any(),
                any()))
                .thenReturn(List.of());

        PageRequest<CinemaRevenueField> pageRequest = new PageRequest<>();
        pageRequest.setPage(1);
        pageRequest.setSize(10);

        CinemaRevenueReportRequest request = CinemaRevenueReportRequest.builder()
                .dateRange(DateRange.builder().from(from).build())
                .pageRequest(pageRequest)
                .build();

        CinemaRevenueReportResponse response = paymentSessionService.getAllCinemaRevenueReport(request);

        assertEquals(from, response.from());
        assertEquals(null, response.to());
        verify(paymentTransactionRepositoryImpl).findAllForRevenueReport(
                anyCollection(),
                isNull(),
                eq(from),
                isNull());
    }

    @Test
    void getAllCinemaRevenueReport_shouldAllowOnlyToDate() {
        UUID cinema1 = UUID.randomUUID();
        LocalDateTime to = LocalDateTime.of(2026, 5, 29, 18, 0);

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
        when(paymentTransactionRepositoryImpl.findAllForRevenueReport(
                anyCollection(),
                isNull(),
                any(),
                any()))
                .thenReturn(List.of());

        PageRequest<CinemaRevenueField> pageRequest = new PageRequest<>();
        pageRequest.setPage(1);
        pageRequest.setSize(10);

        CinemaRevenueReportRequest request = CinemaRevenueReportRequest.builder()
                .dateRange(DateRange.builder().to(to).build())
                .pageRequest(pageRequest)
                .build();

        CinemaRevenueReportResponse response = paymentSessionService.getAllCinemaRevenueReport(request);

        assertEquals(null, response.from());
        assertEquals(to, response.to());
        verify(paymentTransactionRepositoryImpl).findAllForRevenueReport(
                anyCollection(),
                isNull(),
                isNull(),
                eq(to));
    }

    @Test
    void searchFilmRevenueReport_shouldAllowAdminAndAggregateByFilmAcrossCinemas() {
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();

        when(httpRequest.getHeader(com.cinema.http.HeaderNames.X_USER_ROLE))
                .thenReturn(HeaderNames.ROLE_ADMIN);
        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"),
                new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2")));
        when(filmGrpcClient.getFilmTitlesByIds(anyCollection())).thenReturn(Map.of(
                film1, "Film One",
                film2, "Film Two"));

        PaymentTransaction tx1 = buildTransaction(cinema1, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 9, 0), null,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
        PaymentTransaction tx2 = buildTransaction(cinema2, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 10, 0), null,
                BigDecimal.valueOf(200000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
        PaymentTransaction tx3 = buildTransaction(cinema1, film2, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 11, 0), null,
                BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);

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
                    return List.of(tx1, tx2, tx3).stream()
                            .filter(tx -> cinemaIds.contains(tx.getCinemaId()))
                            .filter(tx -> filmIds == null || filmIds.isEmpty() || filmIds.contains(tx.getFilmId()))
                            .toList();
                });

        PageRequest<FilmRevenueField> pageRequest = new PageRequest<>();
        pageRequest.setPage(1);
        pageRequest.setSize(10);

        FilmRevenueReportRequest request = FilmRevenueReportRequest.builder()
                .cinemaIds(List.of(cinema1, cinema2))
                .filmIds(List.of(film1, film2))
                .pageRequest(pageRequest)
                .build();

        FilmRevenueReportResponse response = paymentSessionService.searchFilmRevenueReport(request, httpRequest);

        ArgumentCaptor<List<UUID>> cinemaIdsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<UUID>> filmIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paymentTransactionRepositoryImpl).findAllForRevenueReport(
                cinemaIdsCaptor.capture(),
                filmIdsCaptor.capture(),
                any(),
                any());

        assertEquals(List.of(cinema1, cinema2), cinemaIdsCaptor.getValue());
        assertEquals(List.of(film1, film2), filmIdsCaptor.getValue());
        assertEquals(2L, response.totalElements());
        assertEquals(2, response.items().size());
        assertEquals(film1, response.items().get(0).filmId());
        assertEquals("Film One", response.items().get(0).filmName());
        assertEquals(2L, response.items().get(0).cinemaCount());
        assertEquals(2L, response.items().get(0).paidCount());
        assertEquals(2L, response.items().get(0).totalTransactions());
        assertEquals(BigDecimal.valueOf(300000), response.items().get(0).paidAmount());
        assertEquals(film2, response.items().get(1).filmId());
        assertEquals(1L, response.items().get(1).cinemaCount());
    }

    @Test
    void searchFilmRevenueReport_shouldRestrictStaffScopeAndIntersectRequestedCinemaIds() {
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        when(httpRequest.getHeader(com.cinema.http.HeaderNames.X_USER_ROLE))
                .thenReturn(HeaderNames.ROLE_STAFF);
        when(httpRequest.getHeader(com.cinema.http.HeaderNames.X_USER_ID))
                .thenReturn(requesterId.toString());
        when(cinemaGrpcClient.getCinemasByUserId(requesterId, HeaderNames.ROLE_STAFF)).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
        when(filmGrpcClient.getFilmTitlesByIds(anyCollection())).thenReturn(Map.of(
                film1, "Film One"));

        PaymentTransaction tx1 = buildTransaction(cinema1, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 9, 0), null,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
        PaymentTransaction tx2 = buildTransaction(cinema2, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 10, 0), null,
                BigDecimal.valueOf(200000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
        PaymentTransaction tx3 = buildTransaction(cinema1, film2, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 11, 0), null,
                BigDecimal.valueOf(50000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);

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
                    return List.of(tx1, tx2, tx3).stream()
                            .filter(tx -> cinemaIds.contains(tx.getCinemaId()))
                            .filter(tx -> filmIds == null || filmIds.isEmpty() || filmIds.contains(tx.getFilmId()))
                            .toList();
                });

        PageRequest<FilmRevenueField> pageRequest = new PageRequest<>();
        pageRequest.setPage(1);
        pageRequest.setSize(10);

        FilmRevenueReportRequest request = FilmRevenueReportRequest.builder()
                .cinemaIds(List.of(cinema1, cinema2))
                .filmIds(List.of(film1))
                .pageRequest(pageRequest)
                .build();

        FilmRevenueReportResponse response = paymentSessionService.searchFilmRevenueReport(request, httpRequest);

        ArgumentCaptor<List<UUID>> cinemaIdsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<UUID>> filmIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(paymentTransactionRepositoryImpl).findAllForRevenueReport(
                cinemaIdsCaptor.capture(),
                filmIdsCaptor.capture(),
                any(),
                any());

        assertEquals(List.of(cinema1), cinemaIdsCaptor.getValue());
        assertEquals(List.of(film1), filmIdsCaptor.getValue());
        assertEquals(1, response.items().size());
        assertEquals(film1, response.items().get(0).filmId());
        assertEquals(1L, response.items().get(0).cinemaCount());
        assertEquals(BigDecimal.valueOf(100000), response.items().get(0).paidAmount());
    }

    @Test
    void exportFilmRevenueReport_shouldUseSelectedFilmIdsAndFilmHeaders() throws Exception {
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();

        when(httpRequest.getHeader(com.cinema.http.HeaderNames.X_USER_ROLE))
                .thenReturn(HeaderNames.ROLE_ADMIN);
        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"),
                new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2")));
        when(filmGrpcClient.getFilmTitlesByIds(anyCollection())).thenReturn(Map.of(
                film1, "Film One",
                film2, "Film Two"));

        PaymentTransaction tx1 = buildTransaction(cinema1, film1, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 9, 0), null,
                BigDecimal.valueOf(100000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
        PaymentTransaction tx2 = buildTransaction(cinema2, film2, PaymentTransactionStatus.PAID,
                LocalDateTime.of(2026, 5, 29, 10, 0), null,
                BigDecimal.valueOf(200000), BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);

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
                    return List.of(tx1, tx2).stream()
                            .filter(tx -> cinemaIds.contains(tx.getCinemaId()))
                            .filter(tx -> filmIds == null || filmIds.isEmpty() || filmIds.contains(tx.getFilmId()))
                            .toList();
                });

        PageRequest<FilmRevenueField> pageRequest = new PageRequest<>();
        pageRequest.setPage(1);
        pageRequest.setSize(10);

        FilmRevenueReportRequest request = FilmRevenueReportRequest.builder()
                .cinemaIds(List.of(cinema1, cinema2))
                .filmIds(List.of(film1, film2))
                .selectedIds(List.of(film2))
                .pageRequest(pageRequest)
                .build();

        byte[] file = paymentSessionService.exportFilmRevenueReport(request, httpRequest);

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(file))) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
            assertEquals("FILM REVENUE REPORT", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Film ID", sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Film Name", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Cinema Count", sheet.getRow(1).getCell(2).getStringCellValue());
            assertEquals("Total Transactions", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("Paid Count", sheet.getRow(1).getCell(4).getStringCellValue());
            assertEquals("Refunded Count", sheet.getRow(1).getCell(5).getStringCellValue());
            assertEquals("Paid Amount", sheet.getRow(1).getCell(6).getStringCellValue());
            assertEquals(2, sheet.getLastRowNum());
            assertEquals(film2.toString(), sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Film Two", sheet.getRow(2).getCell(1).getStringCellValue());
            assertEquals(1d, sheet.getRow(2).getCell(2).getNumericCellValue());
            assertEquals(1d, sheet.getRow(2).getCell(3).getNumericCellValue());
            assertEquals(1d, sheet.getRow(2).getCell(4).getNumericCellValue());
            assertEquals(0d, sheet.getRow(2).getCell(5).getNumericCellValue());
            assertEquals(200000d, sheet.getRow(2).getCell(6).getNumericCellValue());
        }
    }

    @Test
    void handleMomoReturn_shouldMarkPaidAndConfirmBooking() {
        String orderId = "PAY-" + UUID.randomUUID();
        PaymentTransaction transaction = buildPendingTransaction(orderId, BigDecimal.valueOf(200000));
        MomoIpnRequest request = buildSignedMomoRequest(orderId, 200000L, 0, "Successful.", 123456789L);

        when(momoGatewayProperties.getAccessKey()).thenReturn("access-key");
        when(momoGatewayProperties.getSecretKey()).thenReturn("secret-key");
        when(paymentTransactionRepository.findByOrderInvoiceNumber(orderId)).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentSessionService.WebhookProcessingResult result = paymentSessionService.handleMomoReturn(request);

        assertEquals(org.springframework.http.HttpStatus.NO_CONTENT, result.status());
        assertEquals(PaymentTransactionStatus.PAID, transaction.getStatus());
        assertEquals("123456789", transaction.getProviderRef());
        verify(bookingGrpcClient, times(1)).confirmBookingPayment(
                transaction.getBookingId(),
                transaction.getAmount(),
                transaction.getPaymentMethod(),
                transaction.getProviderRef(),
                transaction.getOrderInvoiceNumber());
    }

    @Test
    void handleMomoReturn_shouldIgnoreDuplicateEvent() {
        String orderId = "PAY-" + UUID.randomUUID();
        PaymentTransaction transaction = buildPendingTransaction(orderId, BigDecimal.valueOf(150000));
        MomoIpnRequest request = buildSignedMomoRequest(orderId, 150000L, 0, "Successful.", 333444555L);

        when(momoGatewayProperties.getAccessKey()).thenReturn("access-key");
        when(momoGatewayProperties.getSecretKey()).thenReturn("secret-key");
        when(paymentTransactionRepository.findByOrderInvoiceNumber(orderId)).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentSessionService.WebhookProcessingResult first = paymentSessionService.handleMomoReturn(request);
        PaymentSessionService.WebhookProcessingResult second = paymentSessionService.handleMomoReturn(request);

        assertEquals(org.springframework.http.HttpStatus.NO_CONTENT, first.status());
        assertEquals(org.springframework.http.HttpStatus.NO_CONTENT, second.status());
        verify(bookingGrpcClient, times(1)).confirmBookingPayment(
                transaction.getBookingId(),
                transaction.getAmount(),
                transaction.getPaymentMethod(),
                transaction.getProviderRef(),
                transaction.getOrderInvoiceNumber());
    }

    private PaymentTransaction buildPendingTransaction(String orderId, BigDecimal amount) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setBookingId(UUID.randomUUID());
        transaction.setShowtimeId(UUID.randomUUID());
        transaction.setCinemaId(UUID.randomUUID());
        transaction.setFilmId(UUID.randomUUID());
        transaction.setUserId(UUID.randomUUID());
        transaction.setAmount(amount.setScale(0));
        transaction.setCurrency("VND");
        transaction.setPaymentMethod("MOMO_QR");
        transaction.setOrderInvoiceNumber(orderId);
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        return transaction;
    }

    private MomoIpnRequest buildSignedMomoRequest(String orderId, Long amount, Integer resultCode, String message, Long transId) {
        String requestId = orderId;
        String orderInfo = "CinemaStar booking test";
        String orderType = "momo_wallet";
        String partnerCode = "MOMO";
        String payType = "qr";
        String extraData = "";
        long responseTime = System.currentTimeMillis();
        String signatureBase = String.join("&",
                "accessKey=access-key",
                "amount=" + amount,
                "extraData=" + extraData,
                "message=" + message,
                "orderId=" + orderId,
                "orderInfo=" + orderInfo,
                "orderType=" + orderType,
                "partnerCode=" + partnerCode,
                "payType=" + payType,
                "requestId=" + requestId,
                "responseTime=" + responseTime,
                "resultCode=" + resultCode,
                "transId=" + transId);
        String signature = hmacSha256Hex(signatureBase, "secret-key");
        return new MomoIpnRequest(
                partnerCode,
                orderId,
                requestId,
                amount,
                orderInfo,
                orderType,
                transId,
                resultCode,
                message,
                payType,
                responseTime,
                extraData,
                signature);
    }

    private String hmacSha256Hex(String value, String secretKey) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
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

    private void stubSuccessfulCheckout() {
        when(momoGatewayProperties.getRedirectUrl()).thenReturn("https://cinema-api.duckdns.org/payment/result");
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(momoPaymentGatewayClient.createCheckout(any(PaymentTransaction.class), any()))
                .thenReturn(new MomoPaymentGatewayClient.MomoCheckoutResult(
                        "https://momo.example.com/pay",
                        "https://momo.example.com/qr",
                        "{}",
                        "{\"resultCode\":0,\"payUrl\":\"https://momo.example.com/pay\"}"));
    }

    private BookingGrpcClient.BookingPaymentContext buildBookingContext(
            UUID bookingId,
            UUID cinemaId,
            UUID userId,
            String bookingStatus,
            String paymentStatus) {
        return new BookingGrpcClient.BookingPaymentContext(
                bookingId,
                UUID.randomUUID(),
                cinemaId,
                UUID.randomUUID(),
                userId,
                BigDecimal.valueOf(180000),
                LocalDateTime.now().plusMinutes(30),
                bookingStatus,
                paymentStatus,
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(30000),
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(180000));
    }

    private PaymentTransaction buildReusableTransaction(UUID bookingId, UUID userId) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setBookingId(bookingId);
        transaction.setShowtimeId(UUID.randomUUID());
        transaction.setCinemaId(UUID.randomUUID());
        transaction.setFilmId(UUID.randomUUID());
        transaction.setUserId(userId);
        transaction.setAmount(BigDecimal.valueOf(180000).setScale(0));
        transaction.setTicketSubtotalSnapshot(BigDecimal.valueOf(150000).setScale(0));
        transaction.setProductSubtotalSnapshot(BigDecimal.valueOf(30000).setScale(0));
        transaction.setCurrency("VND");
        transaction.setPaymentMethod("MOMO_QR");
        transaction.setOrderInvoiceNumber("INV-" + UUID.randomUUID());
        transaction.setStatus(PaymentTransactionStatus.PAID);
        transaction.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        transaction.setCheckoutPayloadJson("{}");
        return transaction;
    }
}
