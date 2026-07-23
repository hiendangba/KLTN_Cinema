package com.cinema.showtime_service.services;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyField;
import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyUpdateRequest;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface PricingPolicyService {
    ActionMessageResponse createPricingPolicy(PricingPolicyCreateRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updatePricingPolicy(UUID id, PricingPolicyUpdateRequest request,
                                              HttpServletRequest httpRequest);

    ActionMessageResponse deletePricingPolicy(UUID id, HttpServletRequest httpRequest);

    PricingPolicyResponse getPricingPolicyById(UUID id, HttpServletRequest httpRequest);

    PageResponse<PricingPolicyResponse> searchPricingPolicies(PageRequest<PricingPolicyField> request,
                                                              HttpServletRequest httpRequest);
}
