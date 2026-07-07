package com.cinema.user_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.user_service.dto.request.RegisterCustomerRequest;
import com.cinema.user_service.dto.response.CustomerInfoResponse;
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplPhoneLookupTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @Mock
    private IdentityGrpcClient identityGrpcClient;

    @Mock
    private InternalEmailDispatchService internalEmailDispatchService;

    @Mock
    private CustomerRankService customerRankService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(
                userRepository,
                userMapper,
                cinemaGrpcClient,
                identityGrpcClient,
                internalEmailDispatchService,
                new UserAuditEmailService(),
                customerRankService);
    }

    @Test
    void getCustomerByPhone_shouldReturnMinimalCustomerInfo() {
        UUID customerId = UUID.randomUUID();
        User customer = buildUser(customerId, "customer@example.com", "Customer Name", UserEnum.UserRole.CUSTOMER);
        customer.setPhone("0901234567");

        when(userRepository.findByPhoneAndRoleAndIsDeletedFalse("0901234567", UserEnum.UserRole.CUSTOMER))
                .thenReturn(Optional.of(customer));

        CustomerInfoResponse response = service.getCustomerByPhone("0901234567");

        assertThat(response.getId()).isEqualTo(customerId);
        assertThat(response.getName()).isEqualTo("Customer Name");
        assertThat(response.getPhone()).isEqualTo("0901234567");
    }

    @Test
    void createCustomerProfile_shouldRejectDuplicatePhone() {
        UUID customerId = UUID.randomUUID();
        RegisterCustomerRequest request = RegisterCustomerRequest.builder()
                .id(customerId)
                .name("Customer Name")
                .email("customer@example.com")
                .dob(LocalDate.of(2000, 1, 1))
                .gender(UserEnum.Gender.MALE)
                .phone("0901234567")
                .role(UserEnum.UserRole.CUSTOMER)
                .build();

        when(userRepository.existsById(customerId)).thenReturn(false);
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0901234567")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createCustomerProfile(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PHONE_EXISTED);
        verify(userRepository, never()).save(any(User.class));
    }

    private User buildUser(UUID id, String email, String name, UserEnum.UserRole role) {
        return User.builder()
                .id(id)
                .email(email)
                .name(name)
                .role(role)
                .isDeleted(false)
                .build();
    }
}
