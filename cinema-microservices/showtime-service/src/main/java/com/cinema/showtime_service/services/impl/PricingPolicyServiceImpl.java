package com.cinema.showtime_service.services.impl;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyUpdateRequest;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.grpc.CinemaGrpcClient;
import com.cinema.showtime_service.mapper.PricingPolicyMapper;
import com.cinema.showtime_service.repository.PricingPolicyRepository;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import com.cinema.showtime_service.services.PricingPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PricingPolicyServiceImpl implements PricingPolicyService {

    PricingPolicyRepository pricingPolicyRepository;
    PricingPolicyMapper pricingPolicyMapper;
    ShowTimeRepository showTimeRepository;
    CinemaGrpcClient cinemaGrpcClient;

    @Override
    @Transactional
    public ActionMessageResponse createPricingPolicy(PricingPolicyCreateRequest request,
                                                     HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        validatePricingOrder(request.getStandardPrice(), request.getVipPrice(), request.getCouplePrice());
        PricingPolicy pricingPolicy = pricingPolicyMapper.toEntity(request);
        pricingPolicy.setCinemaId(cinemaId);
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("T\u1EA1o ch\u00EDnh s\u00E1ch gi\u00E1 th\u00E0nh c\u00F4ng")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updatePricingPolicy(UUID id, PricingPolicyUpdateRequest request,
                                                     HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        PricingPolicy pricingPolicy = getActivePricingPolicy(id, cinemaId);
        validatePricingPolicyNotUsed(id);
        validatePricingOrder(request.getStandardPrice(), request.getVipPrice(), request.getCouplePrice());
        pricingPolicy.setName(request.getName());
        pricingPolicy.setStandardPrice(request.getStandardPrice());
        pricingPolicy.setVipPrice(request.getVipPrice());
        pricingPolicy.setCouplePrice(request.getCouplePrice());

        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("C\u1EADp nh\u1EADt ch\u00EDnh s\u00E1ch gi\u00E1 th\u00E0nh c\u00F4ng")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deletePricingPolicy(UUID id, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        PricingPolicy pricingPolicy = getActivePricingPolicy(id, cinemaId);
        validatePricingPolicyNotUsed(id);
        pricingPolicy.setIsDeleted(true);
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("X\u00F3a ch\u00EDnh s\u00E1ch gi\u00E1 th\u00E0nh c\u00F4ng")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PricingPolicyResponse getPricingPolicyById(UUID id, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        return pricingPolicyMapper.toResponse(getActivePricingPolicy(id, cinemaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingPolicyResponse> getAllPricingPolicies(HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        return pricingPolicyRepository.findAllByCinemaIdAndIsDeletedFalseOrderByTimeCreatedDesc(cinemaId).stream()
                .map(pricingPolicyMapper::toResponse)
                .toList();
    }

    private PricingPolicy getActivePricingPolicy(UUID id, UUID cinemaId) {
        return pricingPolicyRepository.findByIdAndCinemaIdAndIsDeletedFalse(id, cinemaId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!HeaderNames.ROLE_MANAGER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePricingPolicyNotUsed(UUID pricingPolicyId) {
        if (showTimeRepository.existsByPricingPolicyIdAndIsDeletedFalse(pricingPolicyId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validatePricingOrder(Long standardPrice, Long vipPrice, Long couplePrice) {
        if (!(standardPrice < vipPrice && vipPrice < couplePrice)) {
            throw new BusinessException(ErrorCode.INVALID_PRICING_ORDER);
        }
    }

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        String userIdRaw = httpRequest.getHeader(HeaderNames.X_USER_ID);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return cinemaGrpcClient.getCinemaIdByUserId(UUID.fromString(userIdRaw));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
    }
}
