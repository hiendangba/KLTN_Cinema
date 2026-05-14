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

import java.util.List;
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
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        validatePricingOrder(request.getStandardPrice(), request.getVipPrice(), request.getCouplePrice());
        PricingPolicy pricingPolicy = pricingPolicyMapper.toEntity(request);
        pricingPolicy.setCinemaId(cinemaId);
        pricingPolicyRepository.save(pricingPolicy);
        return ActionMessageResponse.builder()
                .message("Tạo chính sách giá thành công")
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
                .message("Cập nhật chính sách giá thành công")
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
                .message("Xóa chính sách giá thành công")
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

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            return cinemaGrpcClient.getCinemaIdByUserId(userId);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED || ex.getErrorCode() == ErrorCode.INVALID_FORMAT) {
                log.warn("Invalid auth headers method={} path={}", httpRequest.getMethod(), httpRequest.getRequestURI());
            }
            throw ex;
        }
    }
}
