package com.cinema.payment_service.support;

import com.cinema.payment_service.dto.request.PromotionPreviewRequest;
import com.cinema.payment_service.dto.response.PromotionPreviewResponse;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        request.setPromotionCode("");
        request.setOrderAmount(BigDecimal.valueOf(150000));

        PromotionPreviewResponse response = promotionEngine.previewPromotion(request, requesterUserId);

        assertEquals("", response.promotionCode());
        assertEquals(BigDecimal.valueOf(150000).setScale(0), response.originalAmount());
        assertEquals(BigDecimal.ZERO.setScale(0), response.discountAmount());
        assertEquals(BigDecimal.valueOf(150000).setScale(0), response.finalAmount());
        assertEquals("No promotion code", response.note());
    }
}
