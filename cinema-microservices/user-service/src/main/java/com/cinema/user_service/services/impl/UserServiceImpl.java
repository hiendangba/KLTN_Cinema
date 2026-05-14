package com.cinema.user_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.user_service.dto.request.*;
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

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    UserRepository userRepository;
    UserMapper userMapper;

    @Override
    public ActionMessageResponse createCustomerProfile(RegisterCustomerRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        User user = userMapper.toUser(request);
        userRepository.save(user);
        return ActionMessageResponse.builder()
                .message("Tạo profile cho Customer thành công")
                .build();
    }

    @Override
    public ActionMessageResponse createManagerProfile(RegisterManagerRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        User manager = userMapper.toUserManager(request);
        userRepository.save(manager);
        log.info("Manager profile created: managerId={}, email={}", request.getId(), request.getEmail());
        return ActionMessageResponse.builder()
                .message("Tạo profile cho Manager thành công")
                .build();
    }

    @Override
    public ActionMessageResponse createStaffProfile(RegisterStaffRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        User staff = userMapper.toUserStaff(request);
        userRepository.save(staff);
        log.info("Staff profile created: staffId={}, email={}", request.getId(), request.getEmail());
        return ActionMessageResponse.builder()
                .message("Tạo profile cho Staff thành công")
                .build();
    }

    @Override
    public ActionMessageResponse updateCustomerProfile(UpdateCustomerRequest request,
            HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, "updateCustomerProfile");

        User user = userRepository.findByIdAndRole(userUUID, UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is already used by another user
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserCustomer(user, request);
        userRepository.save(user);

        log.info("Customer profile updated: customerId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message("Cập nhật profile cho Customer thành công")
                .build();
    }

    @Override
    public ActionMessageResponse updateManagerProfile(UpdateManagerRequest request, HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);

        User manager = userRepository.findByIdAndRole(userUUID, UserEnum.UserRole.MANAGER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is already used by another user
        if (!manager.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserManager(manager, request);
        userRepository.save(manager);
        log.info("Manager profile updated: managerId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message("Cập nhật profile cho Manager thành công")
                .build();
    }

    @Override
    public ActionMessageResponse updateStaffProfile(UpdateStaffRequest request, HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER, log, "updateStaffProfile");

        User staff = userRepository.findByIdAndRole(userUUID, UserEnum.UserRole.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Check if email is already used by another user
        if (!staff.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserStaff(staff, request);
        userRepository.save(staff);
        log.info("Staff profile updated: staffId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message("Cập nhật profile cho Staff thành công")
                .build();
    }

    @Override
    public UserResponse getMyProfile(HttpServletRequest request) {
        UUID userUUID = RequestAuthUtils.requireUserId(request, ErrorCode.UNAUTHORIZED);

        User user = userRepository.findById(userUUID)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        log.info("User profile loaded: userId={}", userUUID);
        return userMapper.toUserResponse(user);
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
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
    public PageResponse<UserResponse> getAllStaff(PageRequest<?> pageRequest, HttpServletRequest request) {
        RequestAuthUtils.requireAnyRole(request, log, "getAllStaff",
                HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);

        Pageable pageable = pageRequest.toPageable();
        Page<User> userPage = userRepository.findByRole(UserEnum.UserRole.STAFF, pageable);

        return PageResponse.<UserResponse>builder()
                .data(userMapper.toUserResponseList(userPage.getContent()))
                .currentPage(pageRequest.getPageOrDefault())
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .size(pageRequest.getSizeOrDefault())
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    @Override
    public PageResponse<UserResponse> getAllManager(PageRequest<?> pageRequest, HttpServletRequest request) {
        RequestAuthUtils.requireRole(request, HeaderNames.ROLE_ADMIN, log, "getAllManager");

        Pageable pageable = pageRequest.toPageable();
        Page<User> userPage = userRepository.findByRole(UserEnum.UserRole.MANAGER, pageable);

        return PageResponse.<UserResponse>builder()
                .data(userMapper.toUserResponseList(userPage.getContent()))
                .currentPage(pageRequest.getPageOrDefault())
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .size(pageRequest.getSizeOrDefault())
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }
}
