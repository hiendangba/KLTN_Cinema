package com.cinema.identity_service.services.internal;

import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.identity.IdentityAccountPayload;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class IdentityAccountInternalService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void lockAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean changed = false;
        if (user.getStatus() != UserEnum.UserStatus.LOCKED) {
            user.setStatus(UserEnum.UserStatus.LOCKED);
            changed = true;
        }
        if (!Boolean.TRUE.equals(user.getIsDeleted())) {
            user.setIsDeleted(true);
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
            log.info("Identity account locked: userId={}", userId);
            return;
        }

        log.info("Identity account already locked: userId={}", userId);
    }

    public void unlockAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean changed = false;
        if (user.getStatus() != UserEnum.UserStatus.ACTIVE) {
            user.setStatus(UserEnum.UserStatus.ACTIVE);
            changed = true;
        }
        if (!Boolean.FALSE.equals(user.getIsDeleted())) {
            user.setIsDeleted(false);
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
            log.info("Identity account unlocked: userId={}", userId);
            return;
        }

        log.info("Identity account already unlocked: userId={}", userId);
    }

    public IdentityAccountPayload getAccountByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toPayload(user);
    }

    public void updateAccountEmail(UUID userId, String email) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Objects.equals(user.getEmail(), email) && userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        if (!Objects.equals(user.getEmail(), email)) {
            user.setEmail(email);
            userRepository.save(user);
            log.info("Identity account email updated: userId={}, email={}", userId, email);
        } else {
            log.info("Identity account email unchanged: userId={}", userId);
        }
    }

    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        if (Objects.equals(oldPassword, newPassword)) {
            throw new BusinessException(ErrorCode.PASSWORD_DUPLICATED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Identity account password changed: userId={}", userId);
    }

    public void resetPassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_DUPLICATED);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Identity account password reset: userId={}", userId);
    }

    private IdentityAccountPayload toPayload(User user) {
        return IdentityAccountPayload.newBuilder()
                .setId(user.getId().toString())
                .setEmail(blankToEmpty(user.getEmail()))
                .setProvider(blankToEmpty(user.getProvider()))
                .setProviderId(blankToEmpty(user.getProviderId()))
                .setRole(user.getRole() == null ? "" : user.getRole().name())
                .setStatus(user.getStatus() == null ? "" : user.getStatus().name())
                .setIsDeleted(Boolean.TRUE.equals(user.getIsDeleted()))
                .setTimeCreated(user.getTimeCreated() == null ? "" : user.getTimeCreated().toString())
                .setTimeUpdated(user.getTimeUpdated() == null ? "" : user.getTimeUpdated().toString())
                .build();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
