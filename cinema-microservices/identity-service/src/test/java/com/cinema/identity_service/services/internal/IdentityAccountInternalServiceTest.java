package com.cinema.identity_service.services.internal;

import com.cinema.Enum.UserEnum;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class IdentityAccountInternalServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private IdentityAccountInternalService service;

    @Test
    void unlockAccount_shouldSetActiveAndClearDeleted() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, UserEnum.UserStatus.LOCKED, true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.unlockAccount(userId);

        assertThat(user.getStatus()).isEqualTo(UserEnum.UserStatus.ACTIVE);
        assertThat(user.getIsDeleted()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void unlockAccount_whenAlreadyUnlockedShouldBeIdempotent() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, UserEnum.UserStatus.ACTIVE, false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.unlockAccount(userId);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_shouldEncodeAndSaveNewPassword() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, UserEnum.UserStatus.ACTIVE, false);
        user.setPassword("old-hash");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("new-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.changePassword(userId, "OldPass@123", "NewPass@123");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    private User buildUser(UUID id, UserEnum.UserStatus status, boolean isDeleted) {
        return User.builder()
                .id(id)
                .email("test@example.com")
                .password("secret")
                .role(UserEnum.UserRole.CUSTOMER)
                .status(status)
                .isDeleted(isDeleted)
                .build();
    }
}
