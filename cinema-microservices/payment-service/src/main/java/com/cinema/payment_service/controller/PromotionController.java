package com.cinema.payment_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.payment_service.dto.request.PromotionField;
import com.cinema.payment_service.dto.request.PromotionUpsertRequest;
import com.cinema.payment_service.dto.request.UpdatePromotionStatusRequest;
import com.cinema.payment_service.dto.response.PromotionResponse;
import com.cinema.payment_service.services.PromotionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
public class PromotionController extends BaseController {

    private final PromotionService promotionService;

    @PostMapping
    public ResponseEntity<APIResponse<PromotionResponse>> createPromotion(
            @Valid @RequestBody PromotionUpsertRequest request,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        return created(promotionService.createPromotion(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<PromotionResponse>> updatePromotion(
            @PathVariable UUID id,
            @Valid @RequestBody PromotionUpsertRequest request,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        return ok(promotionService.updatePromotion(id, request, httpRequest));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updatePromotionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePromotionStatusRequest request,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        return ok(promotionService.updatePromotionStatus(id, request, httpRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<PromotionResponse>> getPromotionById(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        return ok(promotionService.getPromotionById(id, httpRequest));
    }

    @PostMapping("/search")
    public ResponseEntity<APIResponse<PageResponse<PromotionResponse>>> searchPromotions(
            @Valid @RequestBody PageRequest<PromotionField> request,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        return ok(promotionService.searchPromotions(request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deletePromotion(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);
        return ok(promotionService.deletePromotion(id, httpRequest));
    }
}
