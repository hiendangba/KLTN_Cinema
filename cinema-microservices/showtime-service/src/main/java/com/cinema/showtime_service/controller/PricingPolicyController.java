package com.cinema.showtime_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyField;
import com.cinema.showtime_service.dto.request.PricingPolicyUpdateRequest;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import com.cinema.showtime_service.services.PricingPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/showtimes/pricing-policies")
@RequiredArgsConstructor
public class PricingPolicyController extends BaseController {
    private final PricingPolicyService pricingPolicyService;

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createPricingPolicy(
            @Valid @RequestBody PricingPolicyCreateRequest request,
            HttpServletRequest httpRequest) {
        return created(SuccessMessage.PRICING_POLICY_CREATED,
                pricingPolicyService.createPricingPolicy(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updatePricingPolicy(
            @PathVariable UUID id,
            @Valid @RequestBody PricingPolicyUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ok(SuccessMessage.PRICING_POLICY_UPDATED,
                pricingPolicyService.updatePricingPolicy(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deletePricingPolicy(@PathVariable UUID id,
                                                                                   HttpServletRequest httpRequest) {
        return ok(SuccessMessage.PRICING_POLICY_DELETED,
                pricingPolicyService.deletePricingPolicy(id, httpRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<PricingPolicyResponse>> getPricingPolicyById(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        return ok(SuccessMessage.PRICING_POLICY_FETCHED, pricingPolicyService.getPricingPolicyById(id, httpRequest));
    }

    @PostMapping("/search")
    public ResponseEntity<APIResponse<PageResponse<PricingPolicyResponse>>> searchPricingPolicies(
            @Valid @RequestBody PageRequest<PricingPolicyField> request,
            HttpServletRequest httpRequest) {
        PageResponse<PricingPolicyResponse> response = pricingPolicyService.searchPricingPolicies(request, httpRequest);
        return ok(SuccessMessage.PRICING_POLICIES_SEARCHED, response);
    }
}
