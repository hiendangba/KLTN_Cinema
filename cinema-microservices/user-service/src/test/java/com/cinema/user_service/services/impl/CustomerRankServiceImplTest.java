package com.cinema.user_service.services.impl;

import com.cinema.user_service.dto.response.CustomerRankSnapshot;
import com.cinema.user_service.entity.CustomerRank;
import com.cinema.user_service.enums.CustomerRankStatus;
import com.cinema.user_service.repository.CustomerRankRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRankServiceImplTest {

    @Mock
    private CustomerRankRepository customerRankRepository;

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
        CustomerRank rank = new CustomerRank();
        rank.setCode("BRONZE");
        rank.setName("Bronze");
        rank.setMinLifetimeAmount(BigDecimal.ZERO);
        rank.setEarningAmountUnit(BigDecimal.valueOf(1000));
        rank.setEarningPointsPerUnit(BigDecimal.ONE);
        rank.setLevel(1);
        rank.setStatus(CustomerRankStatus.ACTIVE);
        rank.setIsDeleted(false);

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
}
