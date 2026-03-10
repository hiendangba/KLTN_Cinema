package com.cinema.user_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.RegisterCustomerResponse;
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.mapper.UserMapper;
import com.cinema.user_service.repository.UserRepository;
import com.cinema.user_service.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    UserRepository userRepository;
    UserMapper userMapper;

    @Override
    public RegisterCustomerResponse createCustomerProfile(RegisterCustomerRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        User user = userMapper.toUser(request);
        userRepository.save(user);
        return RegisterCustomerResponse.builder()
                .message("Tạo profile cho Customer thành công")
                .build();
    }

    @Override
    public RegisterCustomerResponse createManagerProfile(RegisterManagerRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        User manager = userMapper.toUserManager(request);
        userRepository.save(manager);
        log.info("Manager profile created: managerId={}, email={}", request.getId(), request.getEmail());
        return RegisterCustomerResponse.builder()
                .message("Tạo profile cho Manager thành công")
                .build();
    }

    @Override
    public RegisterCustomerResponse createStaffProfile(RegisterStaffRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        User staff = userMapper.toUserStaff(request);
        userRepository.save(staff);
        log.info("Staff profile created: staffId={}, email={}", request.getId(), request.getEmail());
        return RegisterCustomerResponse.builder()
                .message("Tạo profile cho Staff thành công")
                .build();
    }

    @Override
    public RegisterCustomerResponse updateCustomerProfile(UpdateCustomerRequest request, HttpServletRequest httpRequest) {
        String userId = httpRequest.getHeader("X-User-ID");

        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UUID userUUID;

        try {
            userUUID = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String role = httpRequest.getHeader("X-User-Role");
        if (!("ADMIN".equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User user = userRepository.findByIdAndRole(userUUID, UserEnum.UserRole.CUSTOMER).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is already used by another user
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserCustomer(user, request);
        userRepository.save(user);

        log.info("Customer profile updated: customerId={}, email={}", userId, request.getEmail());
        return RegisterCustomerResponse.builder()
                .message("Cập nhật profile cho Customer thành công")
                .build();
    }

    @Override
    public RegisterCustomerResponse updateManagerProfile(UpdateManagerRequest request, HttpServletRequest httpRequest) {
        String userId = httpRequest.getHeader("X-User-ID");
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UUID userUUID;
        try {
            userUUID = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User manager = userRepository.findByIdAndRole(userUUID, UserEnum.UserRole.MANAGER).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is already used by another user
        if (!manager.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserManager(manager, request);
        userRepository.save(manager);
        log.info("Manager profile updated: managerId={}, email={}", userId, request.getEmail());
        return RegisterCustomerResponse.builder()
                .message("Cập nhật profile cho Manager thành công")
                .build();
    }

    @Override
    public RegisterCustomerResponse updateStaffProfile(UpdateStaffRequest request, HttpServletRequest httpRequest) {
        String userId = httpRequest.getHeader("X-User-ID");
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UUID userUUID;
        try {
            userUUID = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }


        String role = httpRequest.getHeader("X-User-Role");
        if (!("MANAGER".equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User staff = userRepository.findByIdAndRole(userUUID, UserEnum.UserRole.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is already used by another user
        if (!staff.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserStaff(staff, request);
        userRepository.save(staff);
        log.info("Staff profile updated: staffId={}, email={}", userId, request.getEmail());
        return RegisterCustomerResponse.builder()
                .message("Cập nhật profile cho Staff thành công")
                .build();
    }

    @Override
    public UserExistenceResponse checkUserExists(UUID userId) {
        boolean exists = userRepository.existsById(userId);
        return UserExistenceResponse.builder()
                .exists(exists)
                .message(exists ? "User tồn tại trong hệ thống" : "User không tồn tại trong hệ thống")
                .build();
    }

    @Override
    public List<UserResponse> getAllStaff(HttpServletRequest request) {
        String role = request.getHeader("X-User-Role");
        if (!("ADMIN".equals(role) || "MANAGER".equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return userMapper.toUserResponseList(
                userRepository.findByRole(UserEnum.UserRole.STAFF));
    }

    @Override
    public List<UserResponse> getAllManager(HttpServletRequest request) {
        String role = request.getHeader("X-User-Role");
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return userMapper.toUserResponseList(
                userRepository.findByRole(UserEnum.UserRole.MANAGER));
    }
}
