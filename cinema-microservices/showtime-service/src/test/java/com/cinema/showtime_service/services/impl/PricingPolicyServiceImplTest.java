package com.cinema.showtime_service.services.impl;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.showtime_service.dto.request.PricingPolicyCreateRequest;
import com.cinema.showtime_service.dto.request.PricingPolicyUpdateRequest;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.grpc.CinemaGrpcClient;
import com.cinema.showtime_service.mapper.PricingPolicyMapper;
import com.cinema.showtime_service.repository.PricingPolicyRepository;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingPolicyServiceImplTest {

    @Mock
    private PricingPolicyRepository pricingPolicyRepository;
    @Mock
    private PricingPolicyMapper pricingPolicyMapper;
    @Mock
    private ShowTimeRepository showTimeRepository;
    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @InjectMocks
    private PricingPolicyServiceImpl pricingPolicyService;

    @Test
    void createPricingPolicy_shouldAllowAdminWithoutCinemaAssignment() {
        UUID cinemaId = UUID.randomUUID();
        PricingPolicyCreateRequest request = PricingPolicyCreateRequest.builder()
                .cinemaId(cinemaId)
                .name("Weekday")
                .standardPrice(50000L)
                .vipPrice(70000L)
                .couplePrice(120000L)
                .build();
        PricingPolicy entity = new PricingPolicy();
        entity.setCinemaId(cinemaId);

        when(pricingPolicyMapper.toEntity(request)).thenReturn(entity);

        ActionMessageResponse response = pricingPolicyService.createPricingPolicy(request, adminRequest());

        assertNotNull(response);
        assertEquals(cinemaId, entity.getCinemaId());
        verify(pricingPolicyRepository).save(entity);
        verify(cinemaGrpcClient, never()).getCinemaIdsByUserId(any(), any());
    }

    @Test
    void updatePricingPolicy_shouldAllowAdminWithoutCinemaAssignment() {
        UUID policyId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        PricingPolicy policy = new PricingPolicy();
        policy.setId(policyId);
        policy.setCinemaId(cinemaId);
        policy.setName("Old");
        policy.setStandardPrice(40000L);
        policy.setVipPrice(60000L);
        policy.setCouplePrice(100000L);
        policy.setIsDeleted(false);

        PricingPolicyUpdateRequest request = PricingPolicyUpdateRequest.builder()
                .cinemaId(cinemaId)
                .name("New")
                .standardPrice(50000L)
                .vipPrice(70000L)
                .couplePrice(120000L)
                .build();

        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(policy));
        when(showTimeRepository.existsByPricingPolicyIdAndIsDeletedFalse(policyId)).thenReturn(false);

        ActionMessageResponse response = pricingPolicyService.updatePricingPolicy(policyId, request, adminRequest());

        assertNotNull(response);
        assertEquals("New", policy.getName());
        assertEquals(50000L, policy.getStandardPrice());
        verify(pricingPolicyRepository).save(policy);
        verify(cinemaGrpcClient, never()).getCinemaIdsByUserId(any(), any());
    }

    @Test
    void updatePricingPolicy_shouldRejectAdminWhenRequestCinemaDiffersFromPolicyCinema() {
        UUID policyId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID otherCinemaId = UUID.randomUUID();
        PricingPolicy policy = new PricingPolicy();
        policy.setId(policyId);
        policy.setCinemaId(cinemaId);
        policy.setIsDeleted(false);

        PricingPolicyUpdateRequest request = PricingPolicyUpdateRequest.builder()
                .cinemaId(otherCinemaId)
                .name("Mismatch")
                .standardPrice(50000L)
                .vipPrice(70000L)
                .couplePrice(120000L)
                .build();

        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(policy));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricingPolicyService.updatePricingPolicy(policyId, request, adminRequest()));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void deletePricingPolicy_shouldAllowAdminWithoutCinemaAssignment() {
        UUID policyId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        PricingPolicy policy = new PricingPolicy();
        policy.setId(policyId);
        policy.setCinemaId(cinemaId);
        policy.setIsDeleted(false);

        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(policy));
        when(showTimeRepository.existsByPricingPolicyIdAndIsDeletedFalse(policyId)).thenReturn(false);

        ActionMessageResponse response = pricingPolicyService.deletePricingPolicy(policyId, adminRequest());

        assertNotNull(response);
        assertTrue(policy.getIsDeleted());
        verify(pricingPolicyRepository).save(policy);
        verify(cinemaGrpcClient, never()).getCinemaIdsByUserId(any(), any());
    }

    @Test
    void deletePricingPolicy_shouldReturnSpecificErrorWhenPolicyIsInUse() {
        UUID policyId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        PricingPolicy policy = new PricingPolicy();
        policy.setId(policyId);
        policy.setCinemaId(cinemaId);
        policy.setIsDeleted(false);

        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(policy));
        when(showTimeRepository.existsByPricingPolicyIdAndIsDeletedFalse(policyId)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pricingPolicyService.deletePricingPolicy(policyId, adminRequest()));

        assertEquals(ErrorCode.PRICING_POLICY_IN_USE, ex.getErrorCode());
        verify(pricingPolicyRepository, never()).save(any());
    }

    private HttpServletRequest adminRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_ADMIN);
        return request;
    }
}
