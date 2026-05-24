package com.cinema.showtime_service.services.impl;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
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
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        validateCinemaAccess(request.getCinemaId(), accessibleCinemaIds);
        validatePricingOrder(request.getStandardPrice(), request.getVipPrice(), request.getCouplePrice());

        PricingPolicy pricingPolicy = pricingPolicyMapper.toEntity(request);
        pricingPolicy.setCinemaId(request.getCinemaId());
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("Táº¡o chÃ­nh sÃ¡ch giÃ¡ thÃ nh cÃ´ng")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updatePricingPolicy(UUID id, PricingPolicyUpdateRequest request,
                                                     HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        validateCinemaAccess(request.getCinemaId(), accessibleCinemaIds);

        PricingPolicy pricingPolicy = getActivePricingPolicy(id);
        validatePolicyOwnership(pricingPolicy, request.getCinemaId(), accessibleCinemaIds);
        validatePricingPolicyNotUsed(id);
        validatePricingOrder(request.getStandardPrice(), request.getVipPrice(), request.getCouplePrice());

        pricingPolicy.setName(request.getName());
        pricingPolicy.setStandardPrice(request.getStandardPrice());
        pricingPolicy.setVipPrice(request.getVipPrice());
        pricingPolicy.setCouplePrice(request.getCouplePrice());
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("Cáº­p nháº­t chÃ­nh sÃ¡ch giÃ¡ thÃ nh cÃ´ng")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deletePricingPolicy(UUID id, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);

        PricingPolicy pricingPolicy = getActivePricingPolicy(id);
        validatePolicyAccess(pricingPolicy, accessibleCinemaIds);
        validatePricingPolicyNotUsed(id);
        pricingPolicy.setIsDeleted(true);
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("XÃ³a chÃ­nh sÃ¡ch giÃ¡ thÃ nh cÃ´ng")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PricingPolicyResponse getPricingPolicyById(UUID id, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        PricingPolicy pricingPolicy = getActivePricingPolicy(id);

        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return pricingPolicyMapper.toResponse(pricingPolicy);
        }

        RequestAuthUtils.requireAnyRole(
                httpRequest,
                log,
                "pricing_policy_read_action",
                HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        validatePolicyAccess(pricingPolicy, accessibleCinemaIds);
        return pricingPolicyMapper.toResponse(pricingPolicy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingPolicyResponse> getAllPricingPolicies(HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return pricingPolicyRepository.findAllByIsDeletedFalseOrderByTimeCreatedDesc().stream()
                    .map(pricingPolicyMapper::toResponse)
                    .toList();
        }

        RequestAuthUtils.requireAnyRole(
                httpRequest,
                log,
                "pricing_policy_read_action",
                HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        return pricingPolicyRepository.findAllByCinemaIdInAndIsDeletedFalseOrderByTimeCreatedDesc(accessibleCinemaIds)
                .stream()
                .map(pricingPolicyMapper::toResponse)
                .toList();
    }

    private PricingPolicy getActivePricingPolicy(UUID id) {
        return pricingPolicyRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void validatePolicyAccess(PricingPolicy pricingPolicy, Set<UUID> accessibleCinemaIds) {
        if (!accessibleCinemaIds.contains(pricingPolicy.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePolicyOwnership(PricingPolicy pricingPolicy, UUID requestCinemaId, Set<UUID> accessibleCinemaIds) {
        if (!requestCinemaId.equals(pricingPolicy.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        validatePolicyAccess(pricingPolicy, accessibleCinemaIds);
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER, log, "pricing_policy_manager_action");
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

    private void validateCinemaAccess(UUID cinemaId, Set<UUID> accessibleCinemaIds) {
        if (cinemaId == null || !accessibleCinemaIds.contains(cinemaId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Set<UUID> resolveAccessibleCinemaIdsByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            String role = RequestAuthUtils.requireRoleHeader(httpRequest);
            List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
            if (cinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            return new HashSet<>(cinemaIds);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_FORMAT) {
                log.warn("Invalid auth headers method={} path={}", httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            throw ex;
        }
    }
}
