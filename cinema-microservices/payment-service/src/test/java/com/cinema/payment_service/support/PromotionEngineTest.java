package com.cinema.payment_service.support;

import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.response.PromotionSelectionResponse;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import com.cinema.payment_service.grpc.BookingGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PromotionCinemaRepository;
import com.cinema.payment_service.repository.PromotionFilmRepository;
import com.cinema.payment_service.repository.PromotionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEngineTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionCinemaRepository promotionCinemaRepository;

    @Mock
    private PromotionFilmRepository promotionFilmRepository;

    @Mock
    private BookingGrpcClient bookingGrpcClient;

    @Spy
    private PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

    @InjectMocks
    private PromotionEngine promotionEngine;

    @Test
    void previewPromotion_shouldMapResponseThroughPaymentMapper() {
        UUID requesterUserId = UUID.randomUUID();
        PromotionPreviewRequest request = new PromotionPreviewRequest();
        request.setPromotionId(null);
        request.setPromotionCode("");
        request.setOrderAmount(BigDecimal.valueOf(150000));

        PromotionPreviewResponse response = promotionEngine.previewPromotion(request, requesterUserId);

        assertEquals("", response.promotionCode());
        assertEquals(BigDecimal.valueOf(150000).setScale(0), response.originalAmount());
        assertEquals(BigDecimal.ZERO.setScale(0), response.discountAmount());
        assertEquals(BigDecimal.valueOf(150000).setScale(0), response.finalAmount());
        assertEquals("No promotion code", response.note());
    }

    @Test
    void previewPromotion_shouldResolveByPromotionId() {
        UUID requesterUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID promotionId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID userId = requesterUserId;

        Promotion promotion = new Promotion();
        promotion.setId(promotionId);
        promotion.setCode("CINEMASTAR10");
        promotion.setName("CinemaStar 10%");
        promotion.setDiscountType(PromotionDiscountType.PERCENT);
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setIsDeleted(false);
        promotion.setStartAt(LocalDateTime.now().minusDays(1));
        promotion.setEndAt(LocalDateTime.now().plusDays(1));

        BookingGrpcClient.BookingPaymentContext bookingContext = new BookingGrpcClient.BookingPaymentContext(
                bookingId,
                UUID.randomUUID(),
                cinemaId,
                filmId,
                userId,
                BigDecimal.valueOf(200000),
                LocalDateTime.now().plusMinutes(30),
                "PENDING",
                "UNPAID",
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(50000),
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(200000));

        PromotionPreviewRequest request = new PromotionPreviewRequest();
        request.setBookingId(bookingId);
        request.setPromotionId(promotionId);
        request.setOrderAmount(BigDecimal.valueOf(200000));

        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(promotionRepository.findById(promotionId)).thenReturn(java.util.Optional.of(promotion));

        PromotionPreviewResponse response = promotionEngine.previewPromotion(request, requesterUserId);

        assertEquals(promotionId, response.promotionId());
        assertEquals("CINEMASTAR10", response.promotionCode());
        assertEquals("CinemaStar 10%", response.promotionName());
        assertEquals(BigDecimal.valueOf(200000).setScale(0), response.originalAmount());
        assertEquals(BigDecimal.valueOf(20000).setScale(0), response.discountAmount());
        assertEquals(BigDecimal.valueOf(180000).setScale(0), response.finalAmount());
        assertTrue(response.note().contains("Applied"));
    }

    @Test
    void listSelectablePromotions_shouldReturnApplicableAndDisabledItems() {
        UUID requesterUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();

        Promotion activePromotion = new Promotion();
        activePromotion.setId(UUID.randomUUID());
        activePromotion.setCode("ACTIVE10");
        activePromotion.setName("Active 10%");
        activePromotion.setDiscountType(PromotionDiscountType.PERCENT);
        activePromotion.setDiscountValue(BigDecimal.TEN);
        activePromotion.setStatus(PromotionStatus.ACTIVE);
        activePromotion.setIsDeleted(false);
        activePromotion.setStartAt(LocalDateTime.now().minusDays(1));
        activePromotion.setEndAt(LocalDateTime.now().plusDays(1));

        Promotion scopedPromotion = new Promotion();
        scopedPromotion.setId(UUID.randomUUID());
        scopedPromotion.setCode("SCOPED20");
        scopedPromotion.setName("Scoped 20%");
        scopedPromotion.setDiscountType(PromotionDiscountType.PERCENT);
        scopedPromotion.setDiscountValue(BigDecimal.valueOf(20));
        scopedPromotion.setMinOrderAmount(BigDecimal.valueOf(300000));
        scopedPromotion.setStatus(PromotionStatus.ACTIVE);
        scopedPromotion.setIsDeleted(false);
        scopedPromotion.setStartAt(LocalDateTime.now().minusDays(1));
        scopedPromotion.setEndAt(LocalDateTime.now().plusDays(1));

        BookingGrpcClient.BookingPaymentContext bookingContext = new BookingGrpcClient.BookingPaymentContext(
                bookingId,
                UUID.randomUUID(),
                cinemaId,
                filmId,
                requesterUserId,
                BigDecimal.valueOf(200000),
                LocalDateTime.now().plusMinutes(30),
                "PENDING",
                "UNPAID",
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(50000),
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(200000));

        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(promotionRepository.findAllByIsDeletedFalse()).thenReturn(List.of(activePromotion, scopedPromotion));

        PromotionSelectionResponse response = promotionEngine.listSelectablePromotions(bookingId, requesterUserId);

        assertEquals(bookingId, response.bookingId());
        assertEquals(BigDecimal.valueOf(200000).setScale(0), response.originalAmount());
        assertEquals(2, response.promotions().size());
        assertTrue(response.promotions().stream().anyMatch(item -> item.promotionCode().equals("ACTIVE10") && item.applicable()));
        assertTrue(response.promotions().stream().anyMatch(item -> item.promotionCode().equals("SCOPED20") && !item.applicable()));
        assertTrue(response.promotions().stream().anyMatch(item -> item.promotionCode().equals("SCOPED20")
                && item.note().contains("Min order")));
    }

    @Test
    void listSelectablePromotions_shouldUseCacheForRepeatedRequestsWithSameBookingContext() {
        UUID requesterUserId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();

        Promotion activePromotion = new Promotion();
        activePromotion.setId(UUID.randomUUID());
        activePromotion.setCode("ACTIVE10");
        activePromotion.setName("Active 10%");
        activePromotion.setDiscountType(PromotionDiscountType.PERCENT);
        activePromotion.setDiscountValue(BigDecimal.TEN);
        activePromotion.setStatus(PromotionStatus.ACTIVE);
        activePromotion.setIsDeleted(false);
        activePromotion.setStartAt(LocalDateTime.now().minusDays(1));
        activePromotion.setEndAt(LocalDateTime.now().plusDays(1));

        BookingGrpcClient.BookingPaymentContext bookingContext = new BookingGrpcClient.BookingPaymentContext(
                bookingId,
                UUID.randomUUID(),
                cinemaId,
                filmId,
                requesterUserId,
                BigDecimal.valueOf(200000),
                LocalDateTime.now().plusMinutes(30),
                "PENDING",
                "UNPAID",
                BigDecimal.valueOf(150000),
                BigDecimal.valueOf(50000),
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.valueOf(200000));

        when(bookingGrpcClient.getBookingPaymentContext(bookingId)).thenReturn(bookingContext);
        when(promotionRepository.findAllByIsDeletedFalse()).thenReturn(List.of(activePromotion));

        PromotionSelectionResponse first = promotionEngine.listSelectablePromotions(bookingId, requesterUserId);
        PromotionSelectionResponse second = promotionEngine.listSelectablePromotions(bookingId, requesterUserId);

        assertEquals(first, second);
        assertEquals(1, first.promotions().size());
        assertTrue(first.promotions().get(0).applicable());
        org.mockito.Mockito.verify(bookingGrpcClient, org.mockito.Mockito.times(1)).getBookingPaymentContext(bookingId);
        org.mockito.Mockito.verify(promotionRepository, org.mockito.Mockito.times(1)).findAllByIsDeletedFalse();
    }
}
