package com.cinema.showtime_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyUpdateRequest;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface PricingPolicyService {
    ActionMessageResponse createPricingPolicy(PricingPolicyCreateRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updatePricingPolicy(UUID id, PricingPolicyUpdateRequest request,
                                              HttpServletRequest httpRequest);

    ActionMessageResponse deletePricingPolicy(UUID id, HttpServletRequest httpRequest);

    PricingPolicyResponse getPricingPolicyById(UUID id, HttpServletRequest httpRequest);

    List<PricingPolicyResponse> getAllPricingPolicies(HttpServletRequest httpRequest);
}
