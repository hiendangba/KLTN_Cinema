package com.cinema.payment_service.services.impl;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.Enum.SuccessMessage;
import com.cinema.payment_service.dto.request.PromotionUpsertRequest;
import com.cinema.payment_service.dto.response.PromotionResponse;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.entity.PromotionCinema;
import com.cinema.payment_service.entity.PromotionFilm;
import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PromotionCinemaRepository;
import com.cinema.payment_service.repository.PromotionFilmRepository;
import com.cinema.payment_service.repository.PromotionRepository;
import com.cinema.payment_service.support.PromotionEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceImplTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionCinemaRepository promotionCinemaRepository;

    @Mock
    private PromotionFilmRepository promotionFilmRepository;

    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @Mock
    private PromotionEngine promotionEngine;

    @Spy
    private PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private PromotionServiceImpl promotionService;

    @Test
    void createPromotion_shouldAllowAdminToCreateGlobalPromotion() {
        UUID userId = UUID.randomUUID();
        PromotionUpsertRequest request = PromotionUpsertRequest.builder()
                .code("GLOBAL10")
                .name("Global promo")
                .description("Global discount")
                .discountType(PromotionDiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(7))
                .build();

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("GLOBAL10")).thenReturn(false);
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = promotionService.createPromotion(request, httpRequest);

        ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(captor.capture());
        verify(promotionCinemaRepository, never()).save(any());
        verify(promotionFilmRepository, never()).save(any());

        Promotion saved = captor.getValue();
        assertEquals("GLOBAL10", saved.getCode());
        assertEquals("ADMIN", saved.getCreatedByRole());
        assertEquals(userId, saved.getCreatedByUserId());
        assertEquals(PromotionStatus.ACTIVE, saved.getStatus());
        assertEquals(SuccessMessage.PROMOTION_CREATED.getMessage(), response.getMessage());
    }

    @Test
    void createPromotion_shouldRejectManagerWhenCinemaOutsideScope() {
        UUID userId = UUID.randomUUID();
        UUID allowedCinemaId = UUID.randomUUID();
        UUID blockedCinemaId = UUID.randomUUID();

        PromotionUpsertRequest request = PromotionUpsertRequest.builder()
                .code("MANAGER10")
                .name("Manager promo")
                .discountType(PromotionDiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(20000))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(7))
                .cinemaIds(List.of(blockedCinemaId))
                .build();

        when(httpRequest.getHeader("X-User-Role")).thenReturn("MANAGER");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(cinemaGrpcClient.getCinemasByUserId(userId, "MANAGER"))
                .thenReturn(List.of(new CinemaGrpcClient.CinemaSummary(allowedCinemaId, "Allowed")));
        when(promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("MANAGER10")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> promotionService.createPromotion(request, httpRequest));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(promotionRepository, never()).save(any());
    }

    @Test
    void updatePromotion_shouldToggleStatus() {
        UUID promotionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Promotion promotion = new Promotion();
        promotion.setId(promotionId);
        promotion.setCode("SALE10");
        promotion.setName("Sale");
        promotion.setDiscountType(PromotionDiscountType.PERCENT);
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setIsDeleted(false);

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(promotionRepository.findById(promotionId)).thenReturn(Optional.of(promotion));
        when(promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot("SALE10", promotionId))
                .thenReturn(false);
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionUpsertRequest request = PromotionUpsertRequest.builder()
                .code("SALE10")
                .name("Sale")
                .discountType(PromotionDiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(7))
                .status(PromotionStatus.INACTIVE)
                .build();

        ActionMessageResponse response = promotionService.updatePromotion(
                promotionId,
                request,
                httpRequest);

        assertEquals(PromotionStatus.INACTIVE, promotion.getStatus());
        assertEquals(SuccessMessage.PROMOTION_UPDATED.getMessage(), response.getMessage());
    }

    @Test
    void updatePromotion_shouldReplaceMappingsUsingBulkDeleteAndSaveAll() {
        UUID promotionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();

        Promotion promotion = new Promotion();
        promotion.setId(promotionId);
        promotion.setCode("SALE10");
        promotion.setName("Sale");
        promotion.setDiscountType(PromotionDiscountType.PERCENT);
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setIsDeleted(false);

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(promotionRepository.findById(promotionId)).thenReturn(Optional.of(promotion));
        when(promotionRepository.existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot("SALE10", promotionId))
                .thenReturn(false);
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PromotionUpsertRequest request = PromotionUpsertRequest.builder()
                .code("SALE10")
                .name("Sale")
                .discountType(PromotionDiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(7))
                .cinemaIds(List.of(cinemaId, cinemaId))
                .filmIds(List.of(filmId, filmId))
                .build();

        ActionMessageResponse response = promotionService.updatePromotion(promotionId, request, httpRequest);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Iterable> cinemaCaptor = ArgumentCaptor.forClass(Iterable.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Iterable> filmCaptor = ArgumentCaptor.forClass(Iterable.class);
        org.mockito.InOrder order = inOrder(promotionCinemaRepository, promotionFilmRepository);
        order.verify(promotionCinemaRepository).deleteByPromotionId(promotionId);
        order.verify(promotionFilmRepository).deleteByPromotionId(promotionId);
        order.verify(promotionCinemaRepository).saveAll(cinemaCaptor.capture());
        order.verify(promotionFilmRepository).saveAll(filmCaptor.capture());

        List<PromotionCinema> savedCinemaMappings = new ArrayList<>();
        cinemaCaptor.getValue().forEach(item -> savedCinemaMappings.add((PromotionCinema) item));
        List<PromotionFilm> savedFilmMappings = new ArrayList<>();
        filmCaptor.getValue().forEach(item -> savedFilmMappings.add((PromotionFilm) item));

        assertEquals(1, savedCinemaMappings.size());
        assertEquals(cinemaId, savedCinemaMappings.get(0).getCinemaId());
        assertEquals(1, savedFilmMappings.size());
        assertEquals(filmId, savedFilmMappings.get(0).getFilmId());
        assertEquals(SuccessMessage.PROMOTION_UPDATED.getMessage(), response.getMessage());
    }
}
