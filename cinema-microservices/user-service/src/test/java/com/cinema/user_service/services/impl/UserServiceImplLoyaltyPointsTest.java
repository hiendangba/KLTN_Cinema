package com.cinema.user_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.user_service.dto.request.UpdateCustomerRequest;
import com.cinema.user_service.dto.response.CustomerRankSnapshot;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.grpc.CinemaGrpcClient;
import com.cinema.user_service.grpc.IdentityGrpcClient;
import com.cinema.user_service.mapper.UserMapper;
import com.cinema.user_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.user_service.repository.UserRepository;
import com.cinema.user_service.services.CustomerRankService;
import com.cinema.user_service.services.audit.UserAuditEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplLoyaltyPointsTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @Mock
    private IdentityGrpcClient identityGrpcClient;

    @Mock
    private InternalEmailDispatchService internalEmailDispatchService;

    @Mock
    private CustomerRankService customerRankService;

    private UserMapper userMapper;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = Mappers.getMapper(UserMapper.class);
        service = new UserServiceImpl(
                userRepository,
                userMapper,
                cinemaGrpcClient,
                identityGrpcClient,
                internalEmailDispatchService,
                new UserAuditEmailService(),
                customerRankService);
        lenient().when(customerRankService.resolveRank(any()))
                .thenReturn(defaultRank());
    }

    private CustomerRankSnapshot defaultRank() {
        return new CustomerRankSnapshot(
                null,
                "BRONZE",
                "Bronze",
                BigDecimal.ZERO,
                BigDecimal.valueOf(1000),
                BigDecimal.ONE,
                1);
    }

    @Test
    void getUserById_shouldExposeLoyaltyPoints() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, 2500L);

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        UserResponse response = service.getUserById(userId);

        assertThat(response.getLoyaltyPoints()).isEqualTo(2500L);
    }

    @Test
    void getUserById_shouldLeaveCustomerRankNullWhenNoRankIsConfigured() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, 2500L);

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(customerRankService.resolveRank(any())).thenReturn(null);

        UserResponse response = service.getUserById(userId);

        assertThat(response.getCustomerRank()).isNull();
    }

    @Test
    void addLoyaltyPoints_shouldIncreaseBalance() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, 2500L);

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        long nextBalance = service.addLoyaltyPoints(userId, 500L);

        assertThat(nextBalance).isEqualTo(3000L);
        assertThat(user.getLoyaltyPoints()).isEqualTo(3000L);
        verify(userRepository).save(user);
    }

    @Test
    void deductLoyaltyPoints_shouldRejectWhenInsufficient() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, 2500L);

        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(user));

        var exception = assertThrows(com.cinema.exception.BusinessException.class,
                () -> service.deductLoyaltyPoints(userId, 5000L));

        assertThat(exception.getErrorCode()).isEqualTo(com.cinema.exception.ErrorCode.LOYALTY_POINTS_INSUFFICIENT);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserCustomer_shouldKeepLoyaltyPointsUnchanged() {
        User user = buildUser(UUID.randomUUID(), 2500L);
        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .name("Customer Updated")
                .email("customer.updated@example.com")
                .dob(LocalDate.of(1991, 2, 2))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0911111111")
                .build();

        userMapper.updateUserCustomer(user, request);

        assertThat(user.getLoyaltyPoints()).isEqualTo(2500L);
    }

    private User buildUser(UUID id, long loyaltyPoints) {
        return User.builder()
                .id(id)
                .email("customer@example.com")
                .name("Customer")
                .dob(LocalDate.of(1990, 1, 1))
                .gender(UserEnum.Gender.MALE)
                .phone("0900000000")
                .role(UserEnum.UserRole.CUSTOMER)
                .isDeleted(false)
                .loyaltyPoints(loyaltyPoints)
                .build();
    }
}
