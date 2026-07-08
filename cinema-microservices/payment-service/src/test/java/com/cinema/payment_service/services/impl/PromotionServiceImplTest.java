package com.cinema.payment_service.services.impl;

import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.Enum.SuccessMessage;
import com.cinema.payment_service.dto.request.PromotionField;
import com.cinema.payment_service.dto.request.PromotionUpsertRequest;
import com.cinema.payment_service.dto.response.CustomerRankSummaryResponse;
import com.cinema.payment_service.dto.response.PromotionResponse;
import com.cinema.payment_service.entity.Promotion;
import com.cinema.payment_service.entity.PromotionCinema;
import com.cinema.payment_service.entity.PromotionFilm;
import com.cinema.payment_service.enums.PromotionDiscountType;
import com.cinema.payment_service.enums.PromotionStatus;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import com.cinema.payment_service.grpc.CustomerRankGrpcClient;
import com.cinema.payment_service.mapper.PaymentMapper;
import com.cinema.payment_service.repository.PaymentTransactionPromotionRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
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
    private CustomerRankGrpcClient customerRankGrpcClient;

    @Mock
    private PaymentTransactionPromotionRepository paymentTransactionPromotionRepository;

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
        assertEquals(null, saved.getMinCustomerLifetimeAmount());
        assertEquals(null, saved.getMaxUsageCount());
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
    void createPromotion_shouldRejectWhenRankAndLifetimeAmountAreProvidedTogether() {
        UUID userId = UUID.randomUUID();
        UUID rankId = UUID.randomUUID();

        PromotionUpsertRequest request = PromotionUpsertRequest.builder()
                .code("VIP10")
                .name("VIP promo")
                .discountType(PromotionDiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .minCustomerRankId(rankId)
                .minCustomerLifetimeAmount(BigDecimal.valueOf(500000))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(7))
                .build();

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> promotionService.createPromotion(request, httpRequest));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
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
    void updatePromotion_shouldRejectWhenRankAndLifetimeAmountAreProvidedTogether() {
        UUID promotionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID rankId = UUID.randomUUID();

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());

        PromotionUpsertRequest request = PromotionUpsertRequest.builder()
                .code("SALE10")
                .name("Sale")
                .discountType(PromotionDiscountType.PERCENT)
                .discountValue(BigDecimal.TEN)
                .minCustomerRankId(rankId)
                .minCustomerLifetimeAmount(BigDecimal.valueOf(500000))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(7))
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> promotionService.updatePromotion(promotionId, request, httpRequest));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(promotionRepository, never()).save(any());
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

    @Test
    void getPromotionById_shouldReturnUsageAndEligibilityFields() {
        UUID promotionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID rankId = UUID.randomUUID();
        Promotion promotion = new Promotion();
        promotion.setId(promotionId);
        promotion.setCode("VIP10");
        promotion.setName("VIP promo");
        promotion.setDiscountType(PromotionDiscountType.PERCENT);
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setIsDeleted(false);
        promotion.setMinCustomerRankId(rankId);
        promotion.setMaxUsageCount(10);

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(promotionRepository.findById(promotionId)).thenReturn(Optional.of(promotion));
        when(paymentTransactionPromotionRepository.countReachedPaidUsageByPromotionId(promotionId)).thenReturn(4L);
        when(customerRankGrpcClient.getCustomerRankById(rankId)).thenReturn(
                new CustomerRankGrpcClient.CustomerRankInfo(
                        rankId,
                        "GOLD",
                        "Gold",
                        BigDecimal.valueOf(1000000),
                        BigDecimal.valueOf(1000),
                        BigDecimal.ONE,
                        3,
                        "ACTIVE"));

        PromotionResponse response = promotionService.getPromotionById(promotionId, httpRequest);

        assertEquals(CustomerRankSummaryResponse.builder()
                .id(rankId)
                .code("GOLD")
                .name("Gold")
                .build(), response.getCustomerRank());
        assertEquals(10, response.getMaxUsageCount());
        assertEquals(4L, response.getUsedCount());
        assertEquals(6L, response.getRemainingUsageCount());
    }

    @Test
    void searchPromotions_shouldFilterByMultipleCinemaIdsForAdmin() {
        UUID userId = UUID.randomUUID();
        UUID promotionAId = UUID.randomUUID();
        UUID promotionBId = UUID.randomUUID();
        UUID cinemaAId = UUID.randomUUID();
        UUID cinemaBId = UUID.randomUUID();
        UUID cinemaOtherId = UUID.randomUUID();

        Promotion promotionA = promotion(promotionAId, "PROMO-A");
        Promotion promotionB = promotion(promotionBId, "PROMO-B");

        PageRequest<PromotionField> request = PageRequest.<PromotionField>builder()
                .page(1)
                .size(20)
                .filterBy(List.of(FilterField.<PromotionField>builder()
                        .field(PromotionField.CINEMA_ID)
                        .operator("IN")
                        .value(List.of(cinemaAId, cinemaBId))
                        .build()))
                .build();

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(promotionRepository.findAllByIsDeletedFalse()).thenReturn(List.of(promotionA, promotionB));
        when(promotionCinemaRepository.findAllByPromotionId(promotionAId))
                .thenReturn(List.of(promotionCinema(promotionAId, cinemaAId)));
        when(promotionCinemaRepository.findAllByPromotionId(promotionBId))
                .thenReturn(List.of(promotionCinema(promotionBId, cinemaOtherId)));
        when(promotionCinemaRepository.findAllByPromotionIdIn(List.of(promotionAId)))
                .thenReturn(List.of(promotionCinema(promotionAId, cinemaAId)));
        when(promotionFilmRepository.findAllByPromotionIdIn(List.of(promotionAId))).thenReturn(List.of());
        when(paymentTransactionPromotionRepository.summarizeReachedPaidUsageByPromotionIds(List.of(promotionAId)))
                .thenReturn(List.of());

        var response = promotionService.searchPromotions(request, httpRequest);

        assertEquals(1, response.getData().size());
        assertEquals(promotionAId, response.getData().get(0).getId());
        assertTrue(response.getData().get(0).getCinemaIds().contains(cinemaAId));
    }

    @Test
    void searchPromotions_shouldRejectManagerFilteringOutsideAccessibleCinemas() {
        UUID userId = UUID.randomUUID();
        UUID allowedCinemaId = UUID.randomUUID();
        UUID blockedCinemaId = UUID.randomUUID();

        PageRequest<PromotionField> request = PageRequest.<PromotionField>builder()
                .page(1)
                .size(20)
                .filterBy(List.of(FilterField.<PromotionField>builder()
                        .field(PromotionField.CINEMA_ID)
                        .operator("IN")
                        .value(List.of(allowedCinemaId, blockedCinemaId))
                        .build()))
                .build();

        when(httpRequest.getHeader("X-User-Role")).thenReturn("MANAGER");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(promotionRepository.findAllByIsDeletedFalse()).thenReturn(List.of());
        when(cinemaGrpcClient.getCinemasByUserId(userId, "MANAGER"))
                .thenReturn(List.of(new CinemaGrpcClient.CinemaSummary(allowedCinemaId, "Allowed")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> promotionService.searchPromotions(request, httpRequest));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(cinemaGrpcClient).getCinemasByUserId(eq(userId), eq("MANAGER"));
    }

    @Test
    void searchPromotions_shouldFilterByMultipleFilmIds() {
        UUID userId = UUID.randomUUID();
        UUID promotionAId = UUID.randomUUID();
        UUID promotionBId = UUID.randomUUID();
        UUID filmAId = UUID.randomUUID();
        UUID filmBId = UUID.randomUUID();
        UUID filmOtherId = UUID.randomUUID();

        Promotion promotionA = promotion(promotionAId, "PROMO-A");
        Promotion promotionB = promotion(promotionBId, "PROMO-B");

        PageRequest<PromotionField> request = PageRequest.<PromotionField>builder()
                .page(1)
                .size(20)
                .filterBy(List.of(FilterField.<PromotionField>builder()
                        .field(PromotionField.FILM_ID)
                        .operator("IN")
                        .value(List.of(filmAId, filmBId))
                        .build()))
                .build();

        when(httpRequest.getHeader("X-User-Role")).thenReturn("ADMIN");
        when(httpRequest.getHeader("X-User-ID")).thenReturn(userId.toString());
        when(promotionRepository.findAllByIsDeletedFalse()).thenReturn(List.of(promotionA, promotionB));
        when(promotionFilmRepository.findAllByPromotionId(promotionAId))
                .thenReturn(List.of(promotionFilm(promotionAId, filmAId)));
        when(promotionFilmRepository.findAllByPromotionId(promotionBId))
                .thenReturn(List.of(promotionFilm(promotionBId, filmOtherId)));
        when(promotionCinemaRepository.findAllByPromotionIdIn(List.of(promotionAId))).thenReturn(List.of());
        when(promotionFilmRepository.findAllByPromotionIdIn(List.of(promotionAId)))
                .thenReturn(List.of(promotionFilm(promotionAId, filmAId)));
        when(paymentTransactionPromotionRepository.summarizeReachedPaidUsageByPromotionIds(List.of(promotionAId)))
                .thenReturn(List.of());

        var response = promotionService.searchPromotions(request, httpRequest);

        assertEquals(1, response.getData().size());
        assertEquals(promotionAId, response.getData().get(0).getId());
        assertTrue(response.getData().get(0).getFilmIds().contains(filmAId));
    }

    private Promotion promotion(UUID promotionId, String code) {
        Promotion promotion = new Promotion();
        promotion.setId(promotionId);
        promotion.setCode(code);
        promotion.setName(code);
        promotion.setDiscountType(PromotionDiscountType.PERCENT);
        promotion.setDiscountValue(BigDecimal.TEN);
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setIsDeleted(false);
        promotion.setTimeCreated(LocalDateTime.now());
        return promotion;
    }

    private PromotionCinema promotionCinema(UUID promotionId, UUID cinemaId) {
        PromotionCinema mapping = new PromotionCinema();
        mapping.setPromotionId(promotionId);
        mapping.setCinemaId(cinemaId);
        return mapping;
    }

    private PromotionFilm promotionFilm(UUID promotionId, UUID filmId) {
        PromotionFilm mapping = new PromotionFilm();
        mapping.setPromotionId(promotionId);
        mapping.setFilmId(filmId);
        return mapping;
    }
}
