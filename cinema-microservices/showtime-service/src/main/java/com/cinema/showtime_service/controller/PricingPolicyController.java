package com.cinema.showtime_service.controller;

import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/showtimes/pricing-policies")
@RequiredArgsConstructor
public class PricingPolicyController extends BaseController {
    private final PricingPolicyService pricingPolicyService;

    @PostMapping
    public ResponseEntity<APIResponse<PricingPolicyResponse>> createPricingPolicy(
            @Valid @RequestBody PricingPolicyCreateRequest request,
            HttpServletRequest httpRequest) {
        return created(pricingPolicyService.createPricingPolicy(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<PricingPolicyResponse>> updatePricingPolicy(
            @PathVariable UUID id,
            @Valid @RequestBody PricingPolicyUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ok(pricingPolicyService.updatePricingPolicy(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<Void>> deletePricingPolicy(@PathVariable UUID id, HttpServletRequest httpRequest) {
        pricingPolicyService.deletePricingPolicy(id, httpRequest);
        return ok(null);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<PricingPolicyResponse>> getPricingPolicyById(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        return ok(pricingPolicyService.getPricingPolicyById(id, httpRequest));
    }

    @GetMapping
    public ResponseEntity<APIResponse<List<PricingPolicyResponse>>> getAllPricingPolicies(
            HttpServletRequest httpRequest) {
        return ok(pricingPolicyService.getAllPricingPolicies(httpRequest));
    }
}
