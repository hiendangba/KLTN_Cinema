package com.cinema.user_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.dto.request.SendEmailRequest;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.user_service.dto.request.UpdateCustomerRequest;
import com.cinema.user_service.dto.request.UpdateManagerRequest;
import com.cinema.user_service.dto.request.UpdateStaffRequest;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.grpc.CinemaGrpcClient;
import com.cinema.user_service.grpc.IdentityGrpcClient;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.user_service.mapper.UserMapper;
import com.cinema.user_service.repository.UserRepository;
import com.cinema.user_service.services.audit.UserAuditEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDate;
import java.util.List;
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
    private IdentityGrpcClient identityGrpcClient;

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
                identityGrpcClient,
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
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.CUSTOMER.name());

        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .name("New Name")
                .email("new@email.com")
                .dob(LocalDate.of(1995, 1, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0901234567")
                .oldPassword("OldPass@123")
                .newPassword("NewPass@123")
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
        verify(identityGrpcClient).updateAccountEmail(userId, "new@email.com");
        verify(identityGrpcClient).changePassword(userId, "OldPass@123", "NewPass@123");
    }

    @Test
    void updateCustomerProfile_shouldNotSyncIdentityEmailWhenUnchanged() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, "same@email.com", "Old Name", UserEnum.UserRole.CUSTOMER);
        User actor = buildUser(userId, "same@email.com", "Old Name", UserEnum.UserRole.CUSTOMER);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.CUSTOMER))
                .thenReturn(Optional.of(user));
        when(userRepository.findByIdAndIsDeletedFalse(userId)).thenReturn(Optional.of(actor));
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
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.CUSTOMER.name());

        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .name("New Name")
                .email("same@email.com")
                .dob(LocalDate.of(1995, 1, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0901234567")
                .build();

        service.updateCustomerProfile(request, httpRequest);

        verify(identityGrpcClient, never()).updateAccountEmail(any(), any());
    }

    @Test
    void updateCustomerProfileById_adminShouldResetPasswordWhenProvided() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", actorId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.ADMIN.name());

        User target = buildUser(userId, "customer.old@email.com", "Customer Old", UserEnum.UserRole.CUSTOMER);
        User actor = buildUser(actorId, "admin@email.com", "Admin Actor", UserEnum.UserRole.ADMIN);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.CUSTOMER))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UpdateCustomerRequest request = invocation.getArgument(1);
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setDob(request.getDob());
            user.setGender(request.getGender());
            user.setPhone(request.getPhone());
            return null;
        }).when(userMapper).updateUserCustomer(any(User.class), any(UpdateCustomerRequest.class));

        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .name("Customer New")
                .email("customer.old@email.com")
                .dob(LocalDate.of(1997, 5, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0901111111")
                .newPassword("NewPass@123")
                .build();

        service.updateCustomerProfile(userId, request, httpRequest);

        verify(identityGrpcClient).resetPassword(userId, "NewPass@123");
    }

    @Test
    void updateCustomerProfileById_adminShouldQueueAuditMailWithDiffs() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        User target = buildUser(targetId, "customer.old@email.com", "Customer Old", UserEnum.UserRole.CUSTOMER);
        User actor = buildUser(actorId, "admin@email.com", "Admin Actor", UserEnum.UserRole.ADMIN);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(targetId, UserEnum.UserRole.CUSTOMER))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("customer.new@email.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UpdateCustomerRequest request = invocation.getArgument(1);
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setDob(request.getDob());
            user.setGender(request.getGender());
            user.setPhone(request.getPhone());
            return null;
        }).when(userMapper).updateUserCustomer(any(User.class), any(UpdateCustomerRequest.class));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", actorId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.ADMIN.name());

        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .name("Customer New")
                .email("customer.new@email.com")
                .dob(LocalDate.of(1997, 5, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0901111111")
                .build();

        service.updateCustomerProfile(targetId, request, httpRequest);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(internalEmailDispatchService).sendAsync(captor.capture());

        SendEmailRequest email = captor.getValue();
        assertThat(email.getTo()).isEqualTo("customer.new@email.com");
        assertThat(email.getContent()).contains("Admin Actor");
        assertThat(email.getContent()).contains("Customer New");
        assertThat(target.getEmail()).isEqualTo("customer.new@email.com");
    }

    @Test
    void updateCustomerProfileById_nonAdminShouldBeForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", actorId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.MANAGER.name());

        UpdateCustomerRequest request = UpdateCustomerRequest.builder()
                .name("Customer New")
                .email("customer.new@email.com")
                .dob(LocalDate.of(1997, 5, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0901111111")
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateCustomerProfile(targetId, request, httpRequest));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(internalEmailDispatchService, never()).sendAsync(any());
    }

    @Test
    void updateManagerProfileById_adminShouldQueueAuditMail() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        User target = buildUser(targetId, "manager.old@email.com", "Manager Old", UserEnum.UserRole.MANAGER);
        User actor = buildUser(actorId, "admin@email.com", "Admin Actor", UserEnum.UserRole.ADMIN);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(targetId, UserEnum.UserRole.MANAGER))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("manager.new@email.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UpdateManagerRequest request = invocation.getArgument(1);
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setDob(request.getDob());
            user.setGender(request.getGender());
            user.setPhone(request.getPhone());
            user.setBankCode(request.getBankCode());
            user.setAccountNumber(request.getAccountNumber());
            user.setAccountName(request.getAccountName());
            return null;
        }).when(userMapper).updateUserManager(any(User.class), any(UpdateManagerRequest.class));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", actorId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.ADMIN.name());

        UpdateManagerRequest request = UpdateManagerRequest.builder()
                .name("Manager New")
                .email("manager.new@email.com")
                .dob(LocalDate.of(1992, 5, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0902222222")
                .bankCode("VCB")
                .accountNumber("123")
                .accountName("Manager New")
                .build();

        service.updateManagerProfile(targetId, request, httpRequest);

        verify(internalEmailDispatchService).sendAsync(any());
        assertThat(target.getEmail()).isEqualTo("manager.new@email.com");
    }

    @Test
    void updateManagerProfileById_duplicateEmailShouldThrow() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        User target = buildUser(targetId, "manager.old@email.com", "Manager Old", UserEnum.UserRole.MANAGER);
        User actor = buildUser(actorId, "admin@email.com", "Admin Actor", UserEnum.UserRole.ADMIN);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(targetId, UserEnum.UserRole.MANAGER))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("duplicated@email.com")).thenReturn(true);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", actorId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.ADMIN.name());

        UpdateManagerRequest request = UpdateManagerRequest.builder()
                .name("Manager New")
                .email("duplicated@email.com")
                .dob(LocalDate.of(1992, 5, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0902222222")
                .bankCode("VCB")
                .accountNumber("123")
                .accountName("Manager New")
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateManagerProfile(targetId, request, httpRequest));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_EXISTED);
        verify(internalEmailDispatchService, never()).sendAsync(any());
    }

    @Test
    void updateStaffProfileById_managerWithSharedCinemaShouldSucceed() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();

        User target = buildUser(targetId, "staff.old@email.com", "Staff Old", UserEnum.UserRole.STAFF);
        User actor = buildUser(actorId, "manager@email.com", "Manager Actor", UserEnum.UserRole.MANAGER);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(targetId, UserEnum.UserRole.STAFF))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.existsByEmail("staff.new@email.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cinemaGrpcClient.getCinemaIdsByUserId(actorId, UserEnum.UserRole.MANAGER.name()))
                .thenReturn(List.of(cinemaId));
        when(cinemaGrpcClient.getCinemaIdsByUserId(targetId, UserEnum.UserRole.STAFF.name()))
                .thenReturn(List.of(cinemaId));

        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            UpdateStaffRequest request = invocation.getArgument(1);
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setDob(request.getDob());
            user.setGender(request.getGender());
            user.setPhone(request.getPhone());
            user.setBankCode(request.getBankCode());
            user.setAccountNumber(request.getAccountNumber());
            user.setAccountName(request.getAccountName());
            return null;
        }).when(userMapper).updateUserStaff(any(User.class), any(UpdateStaffRequest.class));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", actorId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.MANAGER.name());

        UpdateStaffRequest request = UpdateStaffRequest.builder()
                .name("Staff New")
                .email("staff.new@email.com")
                .dob(LocalDate.of(1996, 3, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0903333333")
                .bankCode("VCB")
                .accountNumber("456")
                .accountName("Staff New")
                .build();

        service.updateStaffProfile(targetId, request, httpRequest);

        verify(internalEmailDispatchService).sendAsync(any());
        assertThat(target.getEmail()).isEqualTo("staff.new@email.com");
    }

    @Test
    void updateStaffProfileById_managerWithoutSharedCinemaShouldBeForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        User target = buildUser(targetId, "staff.old@email.com", "Staff Old", UserEnum.UserRole.STAFF);

        when(userRepository.findByIdAndRoleAndIsDeletedFalse(targetId, UserEnum.UserRole.STAFF))
                .thenReturn(Optional.of(target));
        when(cinemaGrpcClient.getCinemaIdsByUserId(actorId, UserEnum.UserRole.MANAGER.name()))
                .thenReturn(List.of(UUID.randomUUID()));
        when(cinemaGrpcClient.getCinemaIdsByUserId(targetId, UserEnum.UserRole.STAFF.name()))
                .thenReturn(List.of(UUID.randomUUID()));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", actorId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.MANAGER.name());

        UpdateStaffRequest request = UpdateStaffRequest.builder()
                .name("Staff New")
                .email("staff.new@email.com")
                .dob(LocalDate.of(1996, 3, 1))
                .gender(UserEnum.Gender.FEMALE)
                .phone("0903333333")
                .bankCode("VCB")
                .accountNumber("456")
                .accountName("Staff New")
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateStaffProfile(targetId, request, httpRequest));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        verify(internalEmailDispatchService, never()).sendAsync(any());
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

        verify(identityGrpcClient).lockAccount(targetId);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(internalEmailDispatchService).sendAsync(captor.capture());

        SendEmailRequest email = captor.getValue();
        assertThat(email.getTo()).isEqualTo("staff@email.com");
        assertThat(email.getSubject()).contains("vô hiệu hóa");
        assertThat(email.getContent()).contains("soft delete");
        assertThat(email.getContent()).contains("Admin Actor");
        assertThat(email.getContent()).contains("Target Staff");
        assertThat(email.getContent()).contains("isDeleted: false -&gt; true");
        assertThat(target.getIsDeleted()).isTrue();
    }

    @Test
    void restoreCustomerProfile_shouldQueueRestoreAuditMail() {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User target = buildUser(targetId, "customer@email.com", "Target Customer", UserEnum.UserRole.CUSTOMER);
        target.setIsDeleted(true);
        User actor = buildUser(actorId, "admin@email.com", "Admin Actor", UserEnum.UserRole.ADMIN);

        when(userRepository.findByIdAndRoleAndIsDeletedTrue(targetId, UserEnum.UserRole.CUSTOMER))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.restoreCustomerProfile(targetId, actorId, UserEnum.UserRole.ADMIN);

        verify(identityGrpcClient).unlockAccount(targetId);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(internalEmailDispatchService).sendAsync(captor.capture());

        SendEmailRequest email = captor.getValue();
        assertThat(email.getTo()).isEqualTo("customer@email.com");
        assertThat(email.getSubject()).contains("khôi phục");
        assertThat(email.getContent()).contains("restore");
        assertThat(email.getContent()).contains("Admin Actor");
        assertThat(email.getContent()).contains("Target Customer");
        assertThat(email.getContent()).contains("isDeleted: true -&gt; false");
        assertThat(email.getContent()).contains("accountStatus: locked -&gt; active");
        assertThat(target.getIsDeleted()).isFalse();
    }

    @Test
    void restoreStaffProfile_managerWithSharedCinemaShouldSucceed() {
        UUID targetId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();

        User target = buildUser(targetId, "staff@email.com", "Target Staff", UserEnum.UserRole.STAFF);
        target.setIsDeleted(true);
        User actor = buildUser(actorId, "manager@email.com", "Manager Actor", UserEnum.UserRole.MANAGER);

        when(userRepository.findByIdAndRoleAndIsDeletedTrue(targetId, UserEnum.UserRole.STAFF))
                .thenReturn(Optional.of(target));
        when(userRepository.findByIdAndIsDeletedFalse(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cinemaGrpcClient.getCinemaIdsByUserId(actorId, UserEnum.UserRole.MANAGER.name()))
                .thenReturn(List.of(cinemaId));
        when(cinemaGrpcClient.getCinemaIdsByUserId(targetId, UserEnum.UserRole.STAFF.name()))
                .thenReturn(List.of(cinemaId));

        service.restoreStaffProfile(targetId, actorId, UserEnum.UserRole.MANAGER);

        verify(identityGrpcClient).unlockAccount(targetId);
        verify(internalEmailDispatchService).sendAsync(any());
        assertThat(target.getIsDeleted()).isFalse();
    }

    @Test
    void getUserById_shouldEnrichIdentityAccount() {
        UUID requesterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        User target = buildUser(targetId, "target@email.com", "Target User", UserEnum.UserRole.CUSTOMER);
        UserResponse.IdentityAccountResponse identityAccount = UserResponse.IdentityAccountResponse.builder()
                .id(targetId)
                .email("target@email.com")
                .provider("google")
                .providerId("provider-1")
                .role(UserEnum.UserRole.CUSTOMER.name())
                .status(UserEnum.UserStatus.ACTIVE.name())
                .isDeleted(false)
                .timeCreated(LocalDate.now().atStartOfDay())
                .timeUpdated(LocalDate.now().atStartOfDay())
                .build();
        UserResponse profileResponse = UserResponse.builder()
                .id(targetId)
                .email(target.getEmail())
                .name(target.getName())
                .role(target.getRole())
                .build();

        when(userRepository.findByIdAndIsDeletedFalse(targetId)).thenReturn(Optional.of(target));
        when(userMapper.toUserResponse(target)).thenReturn(profileResponse);
        when(identityGrpcClient.getAccountByUserId(targetId)).thenReturn(identityAccount);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-User-ID", requesterId.toString());
        httpRequest.addHeader("X-User-Role", UserEnum.UserRole.ADMIN.name());

        UserResponse response = service.getUserById(targetId, httpRequest);

        assertThat(response.getIdentityAccount()).isNotNull();
        assertThat(response.getIdentityAccount().getEmail()).isEqualTo("target@email.com");
        verify(identityGrpcClient).getAccountByUserId(targetId);
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
                .isDeleted(false)
                .build();
    }
}
