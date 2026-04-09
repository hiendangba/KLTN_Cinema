package com.cinema.showtime_service.services;

import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyUpdateRequest;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface PricingPolicyService {
    PricingPolicyResponse createPricingPolicy(PricingPolicyCreateRequest request, HttpServletRequest httpRequest);

    PricingPolicyResponse updatePricingPolicy(UUID id, PricingPolicyUpdateRequest request,
            HttpServletRequest httpRequest);

    void deletePricingPolicy(UUID id, HttpServletRequest httpRequest);

    PricingPolicyResponse getPricingPolicyById(UUID id, HttpServletRequest httpRequest);

    List<PricingPolicyResponse> getAllPricingPolicies(HttpServletRequest httpRequest);
}
