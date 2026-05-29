package com.cinema.payment_service.services;

import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.payment_service.dto.request.PromotionField;
import com.cinema.payment_service.dto.request.PromotionUpsertRequest;
import com.cinema.payment_service.dto.request.UpdatePromotionStatusRequest;
import com.cinema.payment_service.dto.response.PromotionResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface PromotionService {
    PromotionResponse createPromotion(PromotionUpsertRequest request, HttpServletRequest httpRequest);

    PromotionResponse updatePromotion(UUID id, PromotionUpsertRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updatePromotionStatus(UUID id, UpdatePromotionStatusRequest request,
            HttpServletRequest httpRequest);

    PromotionResponse getPromotionById(UUID id, HttpServletRequest httpRequest);

    PageResponse<PromotionResponse> searchPromotions(PageRequest<PromotionField> request, HttpServletRequest httpRequest);

    ActionMessageResponse deletePromotion(UUID id, HttpServletRequest httpRequest);
}
