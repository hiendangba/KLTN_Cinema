package com.cinema.user_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SendEmailRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.user_service.grpc.CinemaGrpcClient;
import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.CustomerInfoResponse;
import com.cinema.user_service.dto.response.CustomerRankResponse;
import com.cinema.user_service.dto.response.CustomerRankSnapshot;
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.mapper.UserMapper;
import com.cinema.user_service.repository.UserRepository;
import com.cinema.user_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.user_service.services.CustomerRankService;
import com.cinema.user_service.services.UserService;
import com.cinema.user_service.services.audit.UserAuditEmailService;
import com.cinema.user_service.grpc.IdentityGrpcClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {
    UserRepository userRepository;
    UserMapper userMapper;
    CinemaGrpcClient cinemaGrpcClient;
    IdentityGrpcClient identityGrpcClient;
    InternalEmailDispatchService internalEmailDispatchService;
    UserAuditEmailService userAuditEmailService;
    CustomerRankService customerRankService;

    @Override
    public ActionMessageResponse createCustomerProfile(RegisterCustomerRequest request) {
        if (userRepository.existsById(request.getId())) {
            throw new BusinessException(ErrorCode.ID_EXISTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTED);
        }

        User user = userMapper.toUser(request);
        userRepository.save(user);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_CREATED.getMessage())
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
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTED);
        }

        User manager = userMapper.toUserManager(request);
        userRepository.save(manager);
        log.info("Manager profile created: managerId={}, email={}", request.getId(), request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_CREATED.getMessage())
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
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTED);
        }

        User staff = userMapper.toUserStaff(request);
        userRepository.save(staff);
        log.info("Staff profile created: staffId={}, email={}", request.getId(), request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerInfoResponse getCustomerByPhone(String phone) {
        String normalizedPhone = phone == null ? null : phone.trim();
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User customer = userRepository.findByPhoneAndRoleAndIsDeletedFalse(
                        normalizedPhone,
                        UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return CustomerInfoResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(customer.getPhone())
                .build();
    }

    @Override
    public ActionMessageResponse updateCustomerProfile(UpdateCustomerRequest request,
            HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_CUSTOMER, log, "updateCustomerProfile");

        User user = userRepository.findByIdAndRoleAndIsDeletedFalse(userUUID, UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(user);
        AuditIdentity actor = resolveActor(httpRequest);

        // Check if email is already used by another user
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }
        if (!Objects.equals(user.getPhone(), request.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTED);
        }

        userMapper.updateUserCustomer(user, request);
        userRepository.save(user);
        applyPasswordChangeIfRequested(user.getId(), request.getOldPassword(), request.getNewPassword(), true);
        syncIdentityEmailIfChanged(user.getId(), original.email(), request.getEmail());
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                user,
                actor,
                "update",
                buildCustomerUpdateDiffs(original, request)));

        log.info("Customer profile updated: customerId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_UPDATED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse updateManagerProfile(UpdateManagerRequest request, HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER, log, "updateManagerProfile");
        AuditIdentity actor = resolveActor(httpRequest);

        User manager = userRepository.findByIdAndRoleAndIsDeletedFalse(userUUID, UserEnum.UserRole.MANAGER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(manager);

        // Check if email is already used by another user
        if (!manager.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }
        if (!Objects.equals(manager.getPhone(), request.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTED);
        }

        userMapper.updateUserManager(manager, request);
        userRepository.save(manager);
        applyPasswordChangeIfRequested(manager.getId(), request.getOldPassword(), request.getNewPassword(), true);
        syncIdentityEmailIfChanged(manager.getId(), original.email(), request.getEmail());
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                manager,
                actor,
                "update",
                buildManagerUpdateDiffs(original, request)));
        log.info("Manager profile updated: managerId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_UPDATED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse updateStaffProfile(UpdateStaffRequest request, HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_STAFF, log, "updateStaffProfile");
        AuditIdentity actor = resolveActor(httpRequest);

        User staff = userRepository.findByIdAndRoleAndIsDeletedFalse(userUUID, UserEnum.UserRole.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(staff);

        // Check if email is already used by another user
        if (!staff.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }
        if (!Objects.equals(staff.getPhone(), request.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException(ErrorCode.PHONE_EXISTED);
        }

        userMapper.updateUserStaff(staff, request);
        userRepository.save(staff);
        applyPasswordChangeIfRequested(staff.getId(), request.getOldPassword(), request.getNewPassword(), true);
        syncIdentityEmailIfChanged(staff.getId(), original.email(), request.getEmail());
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                staff,
                actor,
                "update",
                buildStaffUpdateDiffs(original, request)));
        log.info("Staff profile updated: staffId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_UPDATED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse updateCustomerProfile(
            UUID userId,
            UpdateCustomerRequest request,
            HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        requireAdminOnly(actorRole, "updateCustomerProfileById");

        User target = userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        ensureEmailAvailableForUpdate(target.getEmail(), request.getEmail());
        ensurePhoneAvailableForUpdate(target.getPhone(), request.getPhone());
        userMapper.updateUserCustomer(target, request);
        userRepository.save(target);
        applyPasswordChangeIfRequested(target.getId(), request.getOldPassword(), request.getNewPassword(), false);
        syncIdentityEmailIfChanged(target.getId(), original.email(), request.getEmail());
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                target,
                actor,
                "update",
                buildCustomerUpdateDiffs(original, request)));

        log.info("Customer profile updated by id: targetCustomerId={}, actorId={}, actorRole={}, email={}",
                userId, actorId, actorRole, request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_UPDATED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse updateManagerProfile(
            UUID userId,
            UpdateManagerRequest request,
            HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        requireAdminOnly(actorRole, "updateManagerProfileById");

        User target = userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.MANAGER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        ensureEmailAvailableForUpdate(target.getEmail(), request.getEmail());
        ensurePhoneAvailableForUpdate(target.getPhone(), request.getPhone());
        userMapper.updateUserManager(target, request);
        userRepository.save(target);
        applyPasswordChangeIfRequested(target.getId(), request.getOldPassword(), request.getNewPassword(), false);
        syncIdentityEmailIfChanged(target.getId(), original.email(), request.getEmail());
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                target,
                actor,
                "update",
                buildManagerUpdateDiffs(original, request)));

        log.info("Manager profile updated by id: targetManagerId={}, actorId={}, actorRole={}, email={}",
                userId, actorId, actorRole, request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_UPDATED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse updateStaffProfile(
            UUID userId,
            UpdateStaffRequest request,
            HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        requireAdminOrManager(actorRole, "updateStaffProfileById");

        User target = userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (actorRole == UserEnum.UserRole.MANAGER && !hasSharedCinema(actorId, target.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        UserSnapshot original = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        ensureEmailAvailableForUpdate(target.getEmail(), request.getEmail());
        ensurePhoneAvailableForUpdate(target.getPhone(), request.getPhone());
        userMapper.updateUserStaff(target, request);
        userRepository.save(target);
        applyPasswordChangeIfRequested(target.getId(), request.getOldPassword(), request.getNewPassword(), false);
        syncIdentityEmailIfChanged(target.getId(), original.email(), request.getEmail());
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                target,
                actor,
                "update",
                buildStaffUpdateDiffs(original, request)));

        log.info("Staff profile updated by id: targetStaffId={}, actorId={}, actorRole={}, email={}",
                userId, actorId, actorRole, request.getEmail());
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_UPDATED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse deleteCustomerProfile(UUID userId, HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        return deleteCustomerProfile(userId, actorId, actorRole);
    }

    @Override
    public ActionMessageResponse deleteCustomerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole) {
        requireAdminOnly(actorRole, "deleteCustomerProfile");

        User target = userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot snapshot = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        softDelete(target);
        userRepository.save(target);
        lockIdentityAccount(userId);
        queueAuditMailAfterCommit(buildDeleteAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "soft delete",
                List.of("isDeleted: false -> true", "accountStatus: active -> disabled")));

        log.info("Customer profile soft deleted: customerId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_DELETED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse deleteManagerProfile(UUID userId, HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        return deleteManagerProfile(userId, actorId, actorRole);
    }

    @Override
    public ActionMessageResponse deleteManagerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole) {
        requireAdminOnly(actorRole, "deleteManagerProfile");

        User target = userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.MANAGER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot snapshot = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        softDelete(target);
        userRepository.save(target);
        lockIdentityAccount(userId);
        queueAuditMailAfterCommit(buildDeleteAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "soft delete",
                List.of("isDeleted: false -> true", "accountStatus: active -> disabled")));

        log.info("Manager profile soft deleted: managerId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_DELETED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse deleteStaffProfile(UUID userId, HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        return deleteStaffProfile(userId, actorId, actorRole);
    }

    @Override
    public ActionMessageResponse deleteStaffProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole) {
        requireAdminOrManager(actorRole, "deleteStaffProfile");

        User target = userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (actorRole == UserEnum.UserRole.MANAGER && !hasSharedCinema(actorId, target.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        UserSnapshot snapshot = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        softDelete(target);
        userRepository.save(target);
        lockIdentityAccount(userId);
        queueAuditMailAfterCommit(buildDeleteAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "soft delete",
                List.of("isDeleted: false -> true", "accountStatus: active -> disabled")));

        log.info("Staff profile soft deleted: staffId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_DELETED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse restoreCustomerProfile(UUID userId, HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        return restoreCustomerProfile(userId, actorId, actorRole);
    }

    @Override
    public ActionMessageResponse restoreCustomerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole) {
        requireAdminOnly(actorRole, "restoreCustomerProfile");

        User target = userRepository.findByIdAndRoleAndIsDeletedTrue(userId, UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot snapshot = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        restore(target);
        userRepository.save(target);
        unlockIdentityAccount(userId);
        queueAuditMailAfterCommit(buildRestoreAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "restore",
                List.of("isDeleted: true -> false", "accountStatus: locked -> active")));

        log.info("Customer profile restored: customerId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_RESTORED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse restoreManagerProfile(UUID userId, HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        return restoreManagerProfile(userId, actorId, actorRole);
    }

    @Override
    public ActionMessageResponse restoreManagerProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole) {
        requireAdminOnly(actorRole, "restoreManagerProfile");

        User target = userRepository.findByIdAndRoleAndIsDeletedTrue(userId, UserEnum.UserRole.MANAGER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot snapshot = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        restore(target);
        userRepository.save(target);
        unlockIdentityAccount(userId);
        queueAuditMailAfterCommit(buildRestoreAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "restore",
                List.of("isDeleted: true -> false", "accountStatus: locked -> active")));

        log.info("Manager profile restored: managerId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_RESTORED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse restoreStaffProfile(UUID userId, HttpServletRequest httpRequest) {
        UUID actorId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(httpRequest));
        return restoreStaffProfile(userId, actorId, actorRole);
    }

    @Override
    public ActionMessageResponse restoreStaffProfile(UUID userId, UUID actorId, UserEnum.UserRole actorRole) {
        requireAdminOrManager(actorRole, "restoreStaffProfile");

        User target = userRepository.findByIdAndRoleAndIsDeletedTrue(userId, UserEnum.UserRole.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (actorRole == UserEnum.UserRole.MANAGER && !hasSharedCinema(actorId, target.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        UserSnapshot snapshot = snapshot(target);
        AuditIdentity actor = resolveActor(actorId, actorRole);

        restore(target);
        userRepository.save(target);
        unlockIdentityAccount(userId);
        queueAuditMailAfterCommit(buildRestoreAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "restore",
                List.of("isDeleted: true -> false", "accountStatus: locked -> active")));

        log.info("Staff profile restored: staffId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_RESTORED.getMessage())
                .build();
    }

    @Override
    public ActionMessageResponse deleteCustomerProfileForBooking(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User target = userRepository.findByIdAndRoleAndIsDeletedFalse(userId, UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        softDelete(target);
        userRepository.save(target);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PROFILE_DELETED.getMessage())
                .build();
    }

    @Override
    public UserResponse getMyProfile(HttpServletRequest request) {
        UUID userUUID = RequestAuthUtils.requireUserId(request, ErrorCode.UNAUTHORIZED);

        User user = userRepository.findByIdAndIsDeletedFalse(userUUID)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        log.info("User profile loaded: userId={}", userUUID);
        UserResponse response = toRankedUserResponse(user);
        response.setIdentityAccount(identityGrpcClient.getAccountByUserId(userUUID));
        return response;
    }

    @Override
    public UserResponse getUserById(UUID userId, HttpServletRequest httpRequest) {
        UUID requesterUserId = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        String requesterRoleRaw = RequestAuthUtils.requireRoleHeader(httpRequest);

        UserEnum.UserRole requesterRole = parseUserRole(requesterRoleRaw);
        if (requesterRole == UserEnum.UserRole.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User targetUser = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!canViewUser(requesterUserId, requesterRole, targetUser)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        log.info("User profile loaded by id: requesterId={}, requesterRole={}, targetUserId={}, targetRole={}",
                requesterUserId, requesterRole, userId, targetUser.getRole());
        UserResponse response = toRankedUserResponse(targetUser);
        response.setIdentityAccount(identityGrpcClient.getAccountByUserId(userId));
        return response;
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toRankedUserResponse(user);
    }

    @Override
    public long addLoyaltyPoints(UUID userId, long points) {
        return adjustLoyaltyPoints(userId, points, true);
    }

    @Override
    public long deductLoyaltyPoints(UUID userId, long points) {
        return adjustLoyaltyPoints(userId, points, false);
    }

    @Override
    public UserExistenceResponse checkUserExists(UUID userId) {
        boolean exists = userRepository.existsByIdAndIsDeletedFalse(userId);
        return UserExistenceResponse.builder()
                .exists(exists)
                .message(SuccessMessage.USER_EXISTENCE_CHECKED.getMessage())
                .build();
    }

    @Override
    public PageResponse<UserResponse> getAllStaff(PageRequest<?> pageRequest, HttpServletRequest request) {
        RequestAuthUtils.requireAnyRole(request, log, "getAllStaff",
                HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);

        Pageable pageable = pageRequest.toPageable();
        String keyword = pageRequest.getNormalizedKeyword();
        Page<User> userPage = userRepository.searchByRoleAndKeyword(UserEnum.UserRole.STAFF, keyword, pageable);

        return PageResponse.<UserResponse>builder()
                .data(toRankedUserResponses(userPage.getContent()))
                .currentPage(pageRequest.getPageOrDefault())
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .size(pageRequest.getSizeOrDefault())
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    @Override
    public PageResponse<UserResponse> getAllCustomer(PageRequest<?> pageRequest, HttpServletRequest request) {
        RequestAuthUtils.requireAnyRole(request, log, "getAllCustomer",
                HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);

        Pageable pageable = pageRequest.toPageable();
        String keyword = pageRequest.getNormalizedKeyword();
        Page<User> userPage = userRepository.searchByRoleAndKeyword(UserEnum.UserRole.CUSTOMER, keyword, pageable);

        return PageResponse.<UserResponse>builder()
                .data(toRankedUserResponses(userPage.getContent()))
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
        String keyword = pageRequest.getNormalizedKeyword();
        Page<User> userPage = userRepository.searchByRoleAndKeyword(UserEnum.UserRole.MANAGER, keyword, pageable);

        return PageResponse.<UserResponse>builder()
                .data(toRankedUserResponses(userPage.getContent()))
                .currentPage(pageRequest.getPageOrDefault())
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .size(pageRequest.getSizeOrDefault())
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    private UserResponse toRankedUserResponse(User user) {
        UserResponse response = userMapper.toUserResponse(user);
        enrichCustomerRank(response, user);
        return response;
    }

    private List<UserResponse> toRankedUserResponses(List<User> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .map(this::toRankedUserResponse)
                .toList();
    }

    private void enrichCustomerRank(UserResponse response, User user) {
        if (response == null || user == null) {
            return;
        }
        CustomerRankSnapshot rank = customerRankService.resolveRank(user.getLifetimePaidAmount());
        response.setCustomerRank(CustomerRankResponse.builder()
                .id(rank.id())
                .code(rank.code())
                .name(rank.name())
                .minLifetimeAmount(rank.minLifetimeAmount())
                .earningAmountUnit(rank.earningAmountUnit())
                .earningPointsPerUnit(rank.earningPointsPerUnit())
                .level(rank.level())
                .build());
    }

    private void requireAdminOnly(UserEnum.UserRole actorRole, String action) {
        if (actorRole != UserEnum.UserRole.ADMIN) {
            log.warn("Forbidden action={} requiredRole={} actualRole={}", action, HeaderNames.ROLE_ADMIN, actorRole);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireAdminOrManager(UserEnum.UserRole actorRole, String action) {
        if (actorRole != UserEnum.UserRole.ADMIN && actorRole != UserEnum.UserRole.MANAGER) {
            log.warn("Forbidden action={} requiredRoles={}/{} actualRole={}", action, HeaderNames.ROLE_ADMIN,
                    HeaderNames.ROLE_MANAGER, actorRole);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void ensureEmailAvailableForUpdate(String currentEmail, String nextEmail) {
        if (!Objects.equals(currentEmail, nextEmail) && userRepository.existsByEmail(nextEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }
    }

    private void ensurePhoneAvailableForUpdate(String currentPhone, String nextPhone) {
        if (!Objects.equals(currentPhone, nextPhone) && userRepository.existsByPhone(nextPhone)) {
            throw new BusinessException(ErrorCode.PHONE_EXISTED);
        }
    }

    private long adjustLoyaltyPoints(UUID userId, long points, boolean isAddition) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (points < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long currentPoints = user.getLoyaltyPoints() == null ? 0L : user.getLoyaltyPoints();
        if (points == 0) {
            return currentPoints;
        }

        long nextPoints;
        try {
            if (isAddition) {
                nextPoints = Math.addExact(currentPoints, points);
            } else {
                if (currentPoints < points) {
                    throw new BusinessException(ErrorCode.LOYALTY_POINTS_INSUFFICIENT);
                }
                nextPoints = currentPoints - points;
            }
        } catch (ArithmeticException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        user.setLoyaltyPoints(nextPoints);
        userRepository.save(user);
        log.info("User loyalty points updated: userId={}, operation={}, delta={}, current={}, next={}",
                userId,
                isAddition ? "ADD" : "DEDUCT",
                points,
                currentPoints,
                nextPoints);
        return nextPoints;
    }

    private void softDelete(User user) {
        user.setIsDeleted(true);
    }

    private void restore(User user) {
        user.setIsDeleted(false);
    }

    private void lockIdentityAccount(UUID userId) {
        identityGrpcClient.lockAccount(userId);
    }

    private void unlockIdentityAccount(UUID userId) {
        identityGrpcClient.unlockAccount(userId);
    }

    private void syncIdentityEmailIfChanged(UUID userId, String currentEmail, String nextEmail) {
        if (Objects.equals(currentEmail, nextEmail)) {
            return;
        }
        identityGrpcClient.updateAccountEmail(userId, nextEmail);
        log.info("Identity account email synced: userId={}, email={}", userId, nextEmail);
    }

    private void applyPasswordChangeIfRequested(
            UUID userId,
            String oldPassword,
            String newPassword,
            boolean requireOldPassword) {
        if (!hasText(newPassword)) {
            return;
        }

        if (requireOldPassword && !hasText(oldPassword)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (requireOldPassword) {
            identityGrpcClient.changePassword(userId, oldPassword, newPassword);
        } else {
            identityGrpcClient.resetPassword(userId, newPassword);
        }
        log.info("Identity account password updated: userId={}, resetMode={}", userId, !requireOldPassword);
    }

    private UserSnapshot snapshot(User user) {
        return new UserSnapshot(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getDob(),
                user.getGender(),
                user.getPhone(),
                user.getBankCode(),
                user.getAccountNumber(),
                user.getAccountName(),
                user.getRole(),
                Boolean.TRUE.equals(user.getIsDeleted()));
    }

    private AuditIdentity resolveActor(HttpServletRequest request) {
        UUID actorId = RequestAuthUtils.requireUserId(request, ErrorCode.UNAUTHORIZED);
        UserEnum.UserRole actorRole = parseUserRole(RequestAuthUtils.requireRoleHeader(request));
        return resolveActor(actorId, actorRole);
    }

    private AuditIdentity resolveActor(UUID actorId, UserEnum.UserRole actorRole) {
        User actor = userRepository.findByIdAndIsDeletedFalse(actorId).orElse(null);
        String actorName = actor != null && hasText(actor.getName()) ? actor.getName() : null;
        String actorEmail = actor != null && hasText(actor.getEmail()) ? actor.getEmail() : null;
        String displayName = hasText(actorName) ? actorName : (hasText(actorEmail) ? actorEmail : actorId.toString());
        return new AuditIdentity(actorId, actorRole.name(), displayName, actorEmail);
    }

    private SendEmailRequest buildUpdateAuditMail(User target, AuditIdentity actor, String action, List<String> diffs) {
        return userAuditEmailService.buildAuditEmail(
                target.getEmail(),
                "[CinemaStar] Thông báo cập nhật hồ sơ",
                "Cập nhật hồ sơ người dùng",
                actor.displayName(),
                actor.role(),
                displayTargetName(target),
                target.getRole().name(),
                action,
                diffs);
    }

    private SendEmailRequest buildDeleteAuditMail(
            String targetEmail,
            String targetName,
            UserEnum.UserRole targetRole,
            AuditIdentity actor,
            String action,
            List<String> diffs) {
        return userAuditEmailService.buildAuditEmail(
                targetEmail,
                "[CinemaStar] Thông báo vô hiệu hóa tài khoản",
                "Xóa mềm hồ sơ người dùng",
                actor.displayName(),
                actor.role(),
                targetName,
                targetRole.name(),
                action,
                diffs);
    }

    private SendEmailRequest buildRestoreAuditMail(
            String targetEmail,
            String targetName,
            UserEnum.UserRole targetRole,
            AuditIdentity actor,
            String action,
            List<String> diffs) {
        return userAuditEmailService.buildAuditEmail(
                targetEmail,
                "[CinemaStar] Thông báo khôi phục tài khoản",
                "Khôi phục hồ sơ người dùng",
                actor.displayName(),
                actor.role(),
                targetName,
                targetRole.name(),
                action,
                diffs);
    }

    private void queueAuditMailAfterCommit(SendEmailRequest request) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    internalEmailDispatchService.sendAsync(request);
                }
            });
            return;
        }

        internalEmailDispatchService.sendAsync(request);
    }

    private List<String> buildCustomerUpdateDiffs(UserSnapshot before, UpdateCustomerRequest request) {
        List<String> diffs = new ArrayList<>();
        addIfChanged(diffs, "name", before.name(), request.getName());
        addIfChanged(diffs, "email", before.email(), request.getEmail());
        addIfChanged(diffs, "dob", before.dob(), request.getDob());
        addIfChanged(diffs, "gender", before.gender(), request.getGender());
        addIfChanged(diffs, "phone", before.phone(), request.getPhone());
        addPasswordChangeDiff(diffs, request.getNewPassword());
        return normalizeDiffs(diffs);
    }

    private List<String> buildManagerUpdateDiffs(UserSnapshot before, UpdateManagerRequest request) {
        List<String> diffs = new ArrayList<>();
        addIfChanged(diffs, "name", before.name(), request.getName());
        addIfChanged(diffs, "email", before.email(), request.getEmail());
        addIfChanged(diffs, "dob", before.dob(), request.getDob());
        addIfChanged(diffs, "gender", before.gender(), request.getGender());
        addIfChanged(diffs, "phone", before.phone(), request.getPhone());
        addIfChanged(diffs, "bankCode", before.bankCode(), request.getBankCode());
        addIfChanged(diffs, "accountNumber", before.accountNumber(), request.getAccountNumber());
        addIfChanged(diffs, "accountName", before.accountName(), request.getAccountName());
        addPasswordChangeDiff(diffs, request.getNewPassword());
        return normalizeDiffs(diffs);
    }

    private List<String> buildStaffUpdateDiffs(UserSnapshot before, UpdateStaffRequest request) {
        List<String> diffs = new ArrayList<>();
        addIfChanged(diffs, "name", before.name(), request.getName());
        addIfChanged(diffs, "email", before.email(), request.getEmail());
        addIfChanged(diffs, "dob", before.dob(), request.getDob());
        addIfChanged(diffs, "gender", before.gender(), request.getGender());
        addIfChanged(diffs, "phone", before.phone(), request.getPhone());
        addIfChanged(diffs, "bankCode", before.bankCode(), request.getBankCode());
        addIfChanged(diffs, "accountNumber", before.accountNumber(), request.getAccountNumber());
        addIfChanged(diffs, "accountName", before.accountName(), request.getAccountName());
        addPasswordChangeDiff(diffs, request.getNewPassword());
        return normalizeDiffs(diffs);
    }

    private void addPasswordChangeDiff(List<String> diffs, String newPassword) {
        if (hasText(newPassword)) {
            diffs.add("password: changed");
        }
    }

    private List<String> normalizeDiffs(List<String> diffs) {
        if (diffs.isEmpty()) {
            return List.of("Không có thay đổi dữ liệu");
        }
        return diffs;
    }

    private void addIfChanged(List<String> diffs, String field, Object oldValue, Object newValue) {
        if (!Objects.equals(normalizeComparableValue(oldValue), normalizeComparableValue(newValue))) {
            diffs.add(field + ": " + displayValue(oldValue) + " -> " + displayValue(newValue));
        }
    }

    private String normalizeComparableValue(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private String displayValue(Object value) {
        if (value == null) {
            return "N/A";
        }
        if (value instanceof String string && string.isBlank()) {
            return "N/A";
        }
        return value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String displayTargetName(User user) {
        if (user != null && hasText(user.getName())) {
            return user.getName();
        }
        if (user != null && hasText(user.getEmail())) {
            return user.getEmail();
        }
        return user == null ? "N/A" : user.getId().toString();
    }

    private record AuditIdentity(UUID actorId, String role, String displayName, String email) {
    }

    private record UserSnapshot(
            UUID id,
            String email,
            String name,
            java.time.LocalDate dob,
            UserEnum.Gender gender,
            String phone,
            String bankCode,
            String accountNumber,
            String accountName,
            UserEnum.UserRole role,
            boolean deleted) {
    }

    private UserEnum.UserRole parseUserRole(String roleRaw) {
        try {
            return UserEnum.UserRole.valueOf(roleRaw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canViewUser(UUID requesterUserId, UserEnum.UserRole requesterRole, User targetUser) {
        UserEnum.UserRole targetRole = targetUser.getRole();

        return switch (requesterRole) {
            case ADMIN -> true;
            case MANAGER -> targetRole == UserEnum.UserRole.CUSTOMER
                    || (targetRole == UserEnum.UserRole.STAFF
                            && hasSharedCinema(requesterUserId, targetUser.getId()));
            case STAFF -> targetRole == UserEnum.UserRole.CUSTOMER;
            case CUSTOMER -> false;
        };
    }

    private boolean hasSharedCinema(UUID requesterUserId, UUID targetUserId) {
        List<UUID> requesterCinemas = cinemaGrpcClient.getCinemaIdsByUserId(requesterUserId,
                UserEnum.UserRole.MANAGER.name());
        List<UUID> targetCinemas = cinemaGrpcClient.getCinemaIdsByUserId(targetUserId,
                UserEnum.UserRole.STAFF.name());

        if (requesterCinemas.isEmpty() || targetCinemas.isEmpty()) {
            return false;
        }

        Set<UUID> requesterCinemaSet = new HashSet<>(requesterCinemas);
        for (UUID cinemaId : targetCinemas) {
            if (requesterCinemaSet.contains(cinemaId)) {
                return true;
            }
        }

        return false;
    }
}
