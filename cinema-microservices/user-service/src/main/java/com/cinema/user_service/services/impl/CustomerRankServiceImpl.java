package com.cinema.user_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.user_service.dto.request.CustomerRankUpsertRequest;
import com.cinema.user_service.dto.response.CustomerRankResponse;
import com.cinema.user_service.dto.response.CustomerRankSnapshot;
import com.cinema.user_service.entity.CustomerRank;
import com.cinema.user_service.enums.CustomerRankStatus;
import com.cinema.user_service.repository.CustomerRankRepository;
import com.cinema.user_service.services.CustomerRankService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerRankServiceImpl implements CustomerRankService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
    private static final BigDecimal DEFAULT_EARNING_UNIT = BigDecimal.valueOf(1000L);
    private static final BigDecimal DEFAULT_EARNING_POINTS = BigDecimal.ONE;

    private final CustomerRankRepository customerRankRepository;

    @Override
    @Transactional
    public ActionMessageResponse createRank(CustomerRankUpsertRequest request) {
        validateRequest(request);
        String code = normalizeCode(request.getCode());
        if (customerRankRepository.existsByCodeIgnoreCaseAndIsDeletedFalse(code)
                || customerRankRepository.existsByLevelAndIsDeletedFalse(request.getLevel())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        CustomerRank rank = new CustomerRank();
        applyRequest(rank, request, code);
        customerRankRepository.save(rank);
        ensureActiveDefaultRankExists();
        return ActionMessageResponse.builder()
                .message(SuccessMessage.CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateRank(UUID id, CustomerRankUpsertRequest request) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        validateRequest(request);
        String code = normalizeCode(request.getCode());
        if (customerRankRepository.existsByCodeIgnoreCaseAndIsDeletedFalseAndIdNot(code, id)
                || customerRankRepository.existsByLevelAndIsDeletedFalseAndIdNot(request.getLevel(), id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        CustomerRank rank = getEntity(id);
        applyRequest(rank, request, code);
        customerRankRepository.save(rank);
        ensureActiveDefaultRankExists();
        return ActionMessageResponse.builder()
                .message(SuccessMessage.UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerRankResponse getRankById(UUID id) {
        return toResponse(getEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerRankResponse> listRanks() {
        return customerRankRepository.findAllByIsDeletedFalseOrderByLevelAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteRank(UUID id) {
        CustomerRank rank = getEntity(id);
        rank.setStatus(CustomerRankStatus.INACTIVE);
        rank.setIsDeleted(true);
        customerRankRepository.save(rank);
        ensureActiveDefaultRankExists();
        return ActionMessageResponse.builder()
                .message(SuccessMessage.DELETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerRankSnapshot resolveRank(BigDecimal lifetimePaidAmount) {
        BigDecimal amount = normalizeAmount(lifetimePaidAmount);
        return customerRankRepository
                .findFirstByIsDeletedFalseAndStatusAndMinLifetimeAmountLessThanEqualOrderByMinLifetimeAmountDescLevelDesc(
                        CustomerRankStatus.ACTIVE,
                        amount)
                .map(this::toSnapshot)
                .orElse(defaultSnapshot());
    }

    private CustomerRank getEntity(UUID id) {
        return customerRankRepository.findById(id)
                .filter(rank -> !Boolean.TRUE.equals(rank.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void validateRequest(CustomerRankUpsertRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getCode())
                || !StringUtils.hasText(request.getName())
                || request.getMinLifetimeAmount() == null
                || request.getEarningAmountUnit() == null
                || request.getEarningPointsPerUnit() == null
                || request.getLevel() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (request.getMinLifetimeAmount().compareTo(BigDecimal.ZERO) < 0
                || request.getEarningAmountUnit().compareTo(BigDecimal.ZERO) <= 0
                || request.getEarningPointsPerUnit().compareTo(BigDecimal.ZERO) <= 0
                || request.getLevel() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void applyRequest(CustomerRank rank, CustomerRankUpsertRequest request, String normalizedCode) {
        rank.setCode(normalizedCode);
        rank.setName(request.getName().trim());
        rank.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);
        rank.setMinLifetimeAmount(normalizeAmount(request.getMinLifetimeAmount()));
        rank.setEarningAmountUnit(normalizeAmount(request.getEarningAmountUnit()));
        rank.setEarningPointsPerUnit(request.getEarningPointsPerUnit().setScale(2, RoundingMode.HALF_UP));
        rank.setLevel(request.getLevel());
        rank.setStatus(request.getStatus() == null ? CustomerRankStatus.ACTIVE : request.getStatus());
    }

    private void ensureActiveDefaultRankExists() {
        boolean hasDefaultRank = customerRankRepository.findAllByIsDeletedFalseAndStatusOrderByLevelAsc(
                        CustomerRankStatus.ACTIVE)
                .stream()
                .anyMatch(rank -> normalizeAmount(rank.getMinLifetimeAmount()).compareTo(ZERO) == 0);
        if (!hasDefaultRank) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private CustomerRankResponse toResponse(CustomerRank rank) {
        return CustomerRankResponse.builder()
                .id(rank.getId())
                .code(rank.getCode())
                .name(rank.getName())
                .description(rank.getDescription())
                .minLifetimeAmount(normalizeAmount(rank.getMinLifetimeAmount()))
                .earningAmountUnit(normalizeAmount(rank.getEarningAmountUnit()))
                .earningPointsPerUnit(rank.getEarningPointsPerUnit())
                .level(rank.getLevel())
                .status(rank.getStatus())
                .timeCreated(rank.getTimeCreated())
                .timeUpdated(rank.getTimeUpdated())
                .build();
    }

    private CustomerRankSnapshot toSnapshot(CustomerRank rank) {
        return new CustomerRankSnapshot(
                rank.getId(),
                rank.getCode(),
                rank.getName(),
                normalizeAmount(rank.getMinLifetimeAmount()),
                normalizeAmount(rank.getEarningAmountUnit()),
                rank.getEarningPointsPerUnit(),
                rank.getLevel());
    }

    private CustomerRankSnapshot defaultSnapshot() {
        return new CustomerRankSnapshot(
                null,
                "BRONZE",
                "Bronze",
                ZERO,
                DEFAULT_EARNING_UNIT,
                DEFAULT_EARNING_POINTS,
                1);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(0, RoundingMode.HALF_UP);
    }
}
