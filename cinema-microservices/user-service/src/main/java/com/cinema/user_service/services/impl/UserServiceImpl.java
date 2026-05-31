package com.cinema.user_service.services.impl;

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
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.entity.User;
import com.cinema.user_service.mapper.UserMapper;
import com.cinema.user_service.repository.UserRepository;
import com.cinema.user_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.user_service.services.UserService;
import com.cinema.user_service.services.audit.UserAuditEmailService;
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
    InternalEmailDispatchService internalEmailDispatchService;
    UserAuditEmailService userAuditEmailService;

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

        User user = userRepository.findByIdAndRoleAndIsDeletedFalse(userUUID, UserEnum.UserRole.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(user);
        AuditIdentity actor = resolveActor(httpRequest);

        // Check if email is already used by another user
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserCustomer(user, request);
        userRepository.save(user);
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                user,
                actor,
                "update",
                buildCustomerUpdateDiffs(original, request)));

        log.info("Customer profile updated: customerId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message("Cập nhật profile cho Customer thành công")
                .build();
    }

    @Override
    public ActionMessageResponse updateManagerProfile(UpdateManagerRequest request, HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        AuditIdentity actor = resolveActor(httpRequest);

        User manager = userRepository.findByIdAndRoleAndIsDeletedFalse(userUUID, UserEnum.UserRole.MANAGER)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(manager);

        // Check if email is already used by another user
        if (!manager.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserManager(manager, request);
        userRepository.save(manager);
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                manager,
                actor,
                "update",
                buildManagerUpdateDiffs(original, request)));
        log.info("Manager profile updated: managerId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message("Cập nhật profile cho Manager thành công")
                .build();
    }

    @Override
    public ActionMessageResponse updateStaffProfile(UpdateStaffRequest request, HttpServletRequest httpRequest) {
        UUID userUUID = RequestAuthUtils.requireUserId(httpRequest, ErrorCode.UNAUTHORIZED);
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER, log, "updateStaffProfile");
        AuditIdentity actor = resolveActor(httpRequest);

        User staff = userRepository.findByIdAndRoleAndIsDeletedFalse(userUUID, UserEnum.UserRole.STAFF)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        UserSnapshot original = snapshot(staff);

        // Check if email is already used by another user
        if (!staff.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_EXISTED);
        }

        userMapper.updateUserStaff(staff, request);
        userRepository.save(staff);
        queueAuditMailAfterCommit(buildUpdateAuditMail(
                staff,
                actor,
                "update",
                buildStaffUpdateDiffs(original, request)));
        log.info("Staff profile updated: staffId={}, email={}", userUUID, request.getEmail());
        return ActionMessageResponse.builder()
                .message("Cập nhật profile cho Staff thành công")
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
        queueAuditMailAfterCommit(buildDeleteAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "soft delete",
                List.of("isDeleted: false -> true", "accountStatus: active -> disabled")));

        log.info("Customer profile soft deleted: customerId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message("Xóa mềm profile cho Customer thành công")
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
        queueAuditMailAfterCommit(buildDeleteAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "soft delete",
                List.of("isDeleted: false -> true", "accountStatus: active -> disabled")));

        log.info("Manager profile soft deleted: managerId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message("Xóa mềm profile cho Manager thành công")
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
        queueAuditMailAfterCommit(buildDeleteAuditMail(
                snapshot.email(),
                snapshot.name(),
                snapshot.role(),
                actor,
                "soft delete",
                List.of("isDeleted: false -> true", "accountStatus: active -> disabled")));

        log.info("Staff profile soft deleted: staffId={}, actorId={}, actorRole={}", userId, actorId, actorRole);
        return ActionMessageResponse.builder()
                .message("Xóa mềm profile cho Staff thành công")
                .build();
    }

    @Override
    public UserResponse getMyProfile(HttpServletRequest request) {
        UUID userUUID = RequestAuthUtils.requireUserId(request, ErrorCode.UNAUTHORIZED);

        User user = userRepository.findByIdAndIsDeletedFalse(userUUID)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        log.info("User profile loaded: userId={}", userUUID);
        return userMapper.toUserResponse(user);
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
        return userMapper.toUserResponse(targetUser);
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toUserResponse(user);
    }

    @Override
    public UserExistenceResponse checkUserExists(UUID userId) {
        boolean exists = userRepository.existsByIdAndIsDeletedFalse(userId);
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
        Page<User> userPage = userRepository.findByRoleAndIsDeletedFalse(UserEnum.UserRole.STAFF, pageable);

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
    public PageResponse<UserResponse> getAllCustomer(PageRequest<?> pageRequest, HttpServletRequest request) {
        RequestAuthUtils.requireAnyRole(request, log, "getAllCustomer",
                HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);

        Pageable pageable = pageRequest.toPageable();
        Page<User> userPage = userRepository.findByRoleAndIsDeletedFalse(UserEnum.UserRole.CUSTOMER, pageable);

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
        Page<User> userPage = userRepository.findByRoleAndIsDeletedFalse(UserEnum.UserRole.MANAGER, pageable);

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

    private void softDelete(User user) {
        user.setIsDeleted(true);
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
        return normalizeDiffs(diffs);
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
