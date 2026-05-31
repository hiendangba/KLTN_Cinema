package com.cinema.identity_service.services.internal;

import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class IdentityAccountInternalService {

    private final UserRepository userRepository;

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
}
