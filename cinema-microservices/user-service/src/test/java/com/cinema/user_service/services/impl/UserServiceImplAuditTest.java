package com.cinema.user_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.dto.request.SendEmailRequest;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.user_service.dto.request.UpdateCustomerRequest;
import com.cinema.user_service.dto.request.UpdateStaffRequest;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.grpc.CinemaGrpcClient;
import com.cinema.user_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.user_service.mapper.UserMapper;
import com.cinema.user_service.repository.UserRepository;
import com.cinema.user_service.services.audit.UserAuditEmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplAuditTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @Mock
    private InternalEmailDispatchService internalEmailDispatchService;

    private UserAuditEmailService userAuditEmailService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userAuditEmailService = new UserAuditEmailService();
        service = new UserServiceImpl(
                userRepository,
                userMapper,
                cinemaGrpcClient,
                internalEmailDispatchService,
                userAuditEmailService);
    }

    @Test
    void updateCustomerProfile_shouldQueueAuditMailWithDiffs() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "old@email.com", "Old Name", UserEnum.UserRole.CUSTOMER);
        User actor = buildUser(userId, "old@email.com", "Old Name", UserEnum.UserRole.CUSTOMER);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.CUSTOMER))
                .thenReturn(Optional.of(user));
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("new@email.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            User target = invocation.getArgument(0);
            UpdateCustomerRequest request = invocation.getArgument(1);
            target.setName(request.getName());
            target.setEmail(request.getEmail());
            target.setDob(request.getDob());
            target.setGender(request.getGender());
            target.setPhone(request.getPhone());
            return null;
        }).when(userMapper).updateUserCustomer(any(User.class), any(UpdateCustomerRequest.class));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", userId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.ADMIN.name());

        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .name("New Name")
                .email("new@email.com")
                .dob(LocalDate.of(1995, 1, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0901234567")
                .build();

        service.updateCustomerProfile(request, httpRequest);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(internalEmailDispatchService).sendAsync(captor.capture());

        SendEmailRequest email = captor.getValue();
        assertThat(email.getTo()).isEqualTo("new@email.com");
        assertThat(email.getSubject()).contains("cập nhật");
        assertThat(email.getHeader()).contains("Cập nhật hồ sơ");
        assertThat(email.getContent()).contains("Người thao tác");
        assertThat(email.getContent()).contains("Old Name");
        assertThat(email.getContent()).contains("New Name");
        assertThat(email.getContent()).contains("name: Old Name -&gt; New Name");
        assertThat(email.getContent()).contains("email: old@email.com -&gt; new@email.com");
    }

    @Test
    void deleteStaffProfile_shouldQueueSoftDeleteAuditMail() {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User target = buildUser(targetId, "staff@email.com", "Target Staff", UserEnum.UserRole.STAFF);
        User actor = buildUser(actorId, "admin@email.com", "Admin Actor", UserEnum.UserRole.ADMIN);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(targetId, UserEnum.UserRole.STAFF))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteStaffProfile(targetId, actorId, UserEnum.UserRole.ADMIN);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(internalEmailDispatchService).sendAsync(captor.capture());

        SendEmailRequest email = captor.getValue();
        assertThat(email.getTo()).isEqualTo("staff@email.com");
        assertThat(email.getSubject()).contains("vô hiệu hóa");
        assertThat(email.getContent()).contains("soft delete");
        assertThat(email.getContent()).contains("Admin Actor");
        assertThat(email.getContent()).contains("Target Staff");
        assertThat(email.getContent()).contains("isDeleted: false -&gt; true");
        assertThat(target.isDeleted()).isTrue();
    }

    @Test
    void deleteStaffProfile_forbiddenActorShouldNotSendMail() {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteStaffProfile(targetId, actorId, UserEnum.UserRole.CUSTOMER));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(internalEmailDispatchService, never()).sendAsync(any());
    }

    private User buildUser(UUID id, String email, String name, UserEnum.UserRole role) {
        return User.builder()
                .id(id)
                .email(email)
                .name(name)
                .dob(LocalDate.of(1990, 1, 1))
                .gender(UserEnum.Gender.MALE)
                .phone("0900000000")
                .role(role)
                .deleted(false)
                .build();
    }
}
