package com.cinema.user_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.common.OperationReply;
import com.cinema.grpc.identity.ChangePasswordByUserIdRequest;
import com.cinema.grpc.identity.GetUserAccountByUserIdReply;
import com.cinema.grpc.identity.GetUserAccountByUserIdRequest;
import com.cinema.grpc.identity.IdentityInternalServiceGrpc;
import com.cinema.grpc.identity.LockUserAccountRequest;
import com.cinema.grpc.identity.ResetPasswordByUserIdRequest;
import com.cinema.grpc.identity.UpdateUserAccountByUserIdRequest;
import com.cinema.grpc.identity.UnlockUserAccountRequest;
import com.cinema.user_service.dto.response.UserResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class IdentityGrpcClient {

    private final IdentityInternalServiceGrpc.IdentityInternalServiceBlockingStub identityBlockingStub;

    public IdentityGrpcClient(GrpcChannelFactory channelFactory) {
        this.identityBlockingStub = IdentityInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("identity"));
    }

    public void lockAccount(UUID userId) {
        try {
            OperationReply reply = identityBlockingStub.lockUserAccount(
                    LockUserAccountRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void unlockAccount(UUID userId) {
        try {
            OperationReply reply = identityBlockingStub.unlockUserAccount(
                    UnlockUserAccountRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public UserResponse.IdentityAccountResponse getAccountByUserId(UUID userId) {
        try {
            GetUserAccountByUserIdReply reply = identityBlockingStub.getUserAccountByUserId(
                    GetUserAccountByUserIdRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
            if (!reply.hasAccount()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return toIdentityAccountResponse(reply.getAccount());
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void updateAccountEmail(UUID userId, String email) {
        try {
            OperationReply reply = identityBlockingStub.updateUserAccountByUserId(
                    UpdateUserAccountByUserIdRequest.newBuilder()
                            .setUserId(userId.toString())
                            .setEmail(email)
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        try {
            OperationReply reply = identityBlockingStub.changePasswordByUserId(
                    ChangePasswordByUserIdRequest.newBuilder()
                            .setUserId(userId.toString())
                            .setOldPassword(oldPassword)
                            .setNewPassword(newPassword)
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void resetPassword(UUID userId, String newPassword) {
        try {
            OperationReply reply = identityBlockingStub.resetPasswordByUserId(
                    ResetPasswordByUserIdRequest.newBuilder()
                            .setUserId(userId.toString())
                            .setNewPassword(newPassword)
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private UserResponse.IdentityAccountResponse toIdentityAccountResponse(
            com.cinema.grpc.identity.IdentityAccountPayload payload) {
        return UserResponse.IdentityAccountResponse.builder()
                .id(UUID.fromString(payload.getId()))
                .email(payload.getEmail())
                .provider(emptyToNull(payload.getProvider()))
                .providerId(emptyToNull(payload.getProviderId()))
                .role(payload.getRole())
                .status(payload.getStatus())
                .isDeleted(payload.getIsDeleted())
                .timeCreated(parseDateTime(payload.getTimeCreated()))
                .timeUpdated(parseDateTime(payload.getTimeUpdated()))
                .build();
    }

    private LocalDateTime parseDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
