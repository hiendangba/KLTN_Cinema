package com.cinema.user_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.user_service.dto.request.CustomerRankUpsertRequest;
import com.cinema.user_service.dto.response.CustomerRankSnapshot;
import com.cinema.user_service.entity.CustomerRank;
import com.cinema.user_service.enums.CustomerRankStatus;
import com.cinema.user_service.repository.CustomerRankRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRankServiceImplTest {

    @Mock
    private CustomerRankRepository customerRankRepository;

    @Test
    void createRank_shouldPersistWhenRequestIsValidAndDefaultRankExists() {
        CustomerRankServiceImpl service = new CustomerRankServiceImpl(customerRankRepository);
        CustomerRankUpsertRequest request = buildRequest("SILVER", "Bạc", 50000, 1000, 1, 2);
        CustomerRank defaultRank = buildRank("BRONZE", "Đồng", BigDecimal.ZERO, 1000, BigDecimal.ONE, 1,
                CustomerRankStatus.ACTIVE);

        when(customerRankRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("SILVER")).thenReturn(false);
        when(customerRankRepository.existsByLevelAndIsDeletedFalse(2)).thenReturn(false);
        when(customerRankRepository.save(any(CustomerRank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRankRepository.findAllByIsDeletedFalseAndStatusOrderByLevelAsc(CustomerRankStatus.ACTIVE))
                .thenReturn(List.of(defaultRank));

        var response = service.createRank(request);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isNotBlank();
        verify(customerRankRepository).save(any(CustomerRank.class));
    }

    @Test
    void createRank_shouldThrowSpecificErrorWhenCodeAlreadyExists() {
        CustomerRankServiceImpl service = new CustomerRankServiceImpl(customerRankRepository);
        CustomerRankUpsertRequest request = buildRequest("BRONZE", "Đồng", 0, 1000, 1, 1);

        when(customerRankRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("BRONZE")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.createRank(request));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_RANK_CODE_EXISTED);
    }

    @Test
    void createRank_shouldThrowSpecificErrorWhenLevelAlreadyExists() {
        CustomerRankServiceImpl service = new CustomerRankServiceImpl(customerRankRepository);
        CustomerRankUpsertRequest request = buildRequest("SILVER", "Bạc", 50000, 1000, 1, 2);

        when(customerRankRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("SILVER")).thenReturn(false);
        when(customerRankRepository.existsByLevelAndIsDeletedFalse(2)).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.createRank(request));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_RANK_LEVEL_EXISTED);
    }

    @Test
    void createRank_shouldThrowSpecificErrorWhenNoDefaultRankExists() {
        CustomerRankServiceImpl service = new CustomerRankServiceImpl(customerRankRepository);
        CustomerRankUpsertRequest request = buildRequest("SILVER", "Bạc", 50000, 1000, 1, 2);

        when(customerRankRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("SILVER")).thenReturn(false);
        when(customerRankRepository.existsByLevelAndIsDeletedFalse(2)).thenReturn(false);
        when(customerRankRepository.save(any(CustomerRank.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRankRepository.findAllByIsDeletedFalseAndStatusOrderByLevelAsc(CustomerRankStatus.ACTIVE))
                .thenReturn(List.of());

        Throwable thrown = catchThrowable(() -> service.createRank(request));

        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.CUSTOMER_RANK_DEFAULT_REQUIRED);
    }

    @Test
    void resolveRank_shouldReturnNullWhenNoRankIsConfigured() {
        CustomerRankServiceImpl service = new CustomerRankServiceImpl(customerRankRepository);

        when(customerRankRepository
                .findFirstByIsDeletedFalseAndStatusAndMinLifetimeAmountLessThanEqualOrderByMinLifetimeAmountDescLevelDesc(
                        CustomerRankStatus.ACTIVE,
                        BigDecimal.ZERO))
                .thenReturn(Optional.empty());

        CustomerRankSnapshot snapshot = service.resolveRank(null);

        assertThat(snapshot).isNull();
    }

    @Test
    void resolveRank_shouldReturnConfiguredSnapshotWhenMatchExists() {
        CustomerRankServiceImpl service = new CustomerRankServiceImpl(customerRankRepository);
        CustomerRank rank = buildRank("BRONZE", "Bronze", BigDecimal.ZERO, 1000, BigDecimal.ONE, 1,
                CustomerRankStatus.ACTIVE);

        when(customerRankRepository
                .findFirstByIsDeletedFalseAndStatusAndMinLifetimeAmountLessThanEqualOrderByMinLifetimeAmountDescLevelDesc(
                        CustomerRankStatus.ACTIVE,
                        BigDecimal.ZERO))
                .thenReturn(Optional.of(rank));

        CustomerRankSnapshot snapshot = service.resolveRank(BigDecimal.ZERO);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.code()).isEqualTo("BRONZE");
        assertThat(snapshot.name()).isEqualTo("Bronze");
        assertThat(snapshot.level()).isEqualTo(1);
        assertThat(snapshot.minLifetimeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snapshot.earningAmountUnit()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(snapshot.earningPointsPerUnit()).isEqualByComparingTo(BigDecimal.ONE);
    }

    private CustomerRankUpsertRequest buildRequest(String code,
                                                   String name,
                                                   int minLifetimeAmount,
                                                   int earningAmountUnit,
                                                   int earningPointsPerUnit,
                                                   int level) {
        CustomerRankUpsertRequest request = new CustomerRankUpsertRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription("Mô tả");
        request.setMinLifetimeAmount(BigDecimal.valueOf(minLifetimeAmount));
        request.setEarningAmountUnit(BigDecimal.valueOf(earningAmountUnit));
        request.setEarningPointsPerUnit(BigDecimal.valueOf(earningPointsPerUnit));
        request.setLevel(level);
        request.setStatus(CustomerRankStatus.ACTIVE);
        return request;
    }

    private CustomerRank buildRank(String code,
                                   String name,
                                   BigDecimal minLifetimeAmount,
                                   int earningAmountUnit,
                                   BigDecimal earningPointsPerUnit,
                                   int level,
                                   CustomerRankStatus status) {
        CustomerRank rank = new CustomerRank();
        rank.setCode(code);
        rank.setName(name);
        rank.setMinLifetimeAmount(minLifetimeAmount);
        rank.setEarningAmountUnit(BigDecimal.valueOf(earningAmountUnit));
        rank.setEarningPointsPerUnit(earningPointsPerUnit);
        rank.setLevel(level);
        rank.setStatus(status);
        rank.setIsDeleted(false);
        return rank;
    }
}
