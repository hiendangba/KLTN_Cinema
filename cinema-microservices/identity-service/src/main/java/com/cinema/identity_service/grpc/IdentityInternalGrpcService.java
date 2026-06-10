package com.cinema.identity_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.common.OperationReply;
import com.cinema.grpc.identity.CreateCustomerProfileReply;
import com.cinema.grpc.identity.CreateCustomerProfileRequest;
import com.cinema.grpc.identity.DeleteCustomerProfileRequest;
import com.cinema.grpc.identity.ChangePasswordByUserIdRequest;
import com.cinema.grpc.identity.GetUserAccountByUserIdReply;
import com.cinema.grpc.identity.GetUserAccountByUserIdRequest;
import com.cinema.grpc.identity.IdentityInternalServiceGrpc;
import com.cinema.grpc.identity.LockUserAccountRequest;
import com.cinema.grpc.identity.UpdateUserAccountByUserIdRequest;
import com.cinema.grpc.identity.ResetPasswordByUserIdRequest;
import com.cinema.grpc.identity.UnlockUserAccountRequest;
import com.cinema.identity_service.dto.request.CreateCustomerRequest;
import com.cinema.identity_service.services.internal.IdentityAccountInternalService;
import com.cinema.identity_service.services.UserService;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IdentityInternalGrpcService extends IdentityInternalServiceGrpc.IdentityInternalServiceImplBase
        implements BindableService {

    private final IdentityAccountInternalService identityAccountInternalService;
    private final UserService userService;

    @Override
    public void lockUserAccount(
            LockUserAccountRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            identityAccountInternalService.lockAccount(userId);
            responseObserver.onNext(success("Identity account locked successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex.getErrorCode()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while locking identity account", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void unlockUserAccount(
            UnlockUserAccountRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            identityAccountInternalService.unlockAccount(userId);
            responseObserver.onNext(success("Identity account unlocked successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex.getErrorCode()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while unlocking identity account", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getUserAccountByUserId(
            GetUserAccountByUserIdRequest request,
            StreamObserver<GetUserAccountByUserIdReply> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            responseObserver.onNext(GetUserAccountByUserIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Identity account fetched successfully")
                    .setAccount(identityAccountInternalService.getAccountByUserId(userId))
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetUserAccountByUserIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetUserAccountByUserIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching identity account", ex);
            responseObserver.onNext(GetUserAccountByUserIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateUserAccountByUserId(
            UpdateUserAccountByUserIdRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            identityAccountInternalService.updateAccountEmail(userId, request.getEmail());
            responseObserver.onNext(success("Identity account email updated successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex.getErrorCode()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while updating identity account", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void changePasswordByUserId(
            ChangePasswordByUserIdRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            identityAccountInternalService.changePassword(
                    userId,
                    request.getOldPassword(),
                    request.getNewPassword());
            responseObserver.onNext(success("Identity account password changed successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex.getErrorCode()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while changing identity password", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void resetPasswordByUserId(
            ResetPasswordByUserIdRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            identityAccountInternalService.resetPassword(userId, request.getNewPassword());
            responseObserver.onNext(success("Identity account password reset successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex.getErrorCode()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while resetting identity password", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void createCustomerProfile(
            CreateCustomerProfileRequest request,
            StreamObserver<CreateCustomerProfileReply> responseObserver) {
        try {
            UUID userId = userService.createCustomer(CreateCustomerRequest.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .phone(request.getPhone())
                    .dob(parseOptionalDate(request.getDob()))
                    .gender(parseOptionalGender(request.getGender()))
                    .build());
            responseObserver.onNext(CreateCustomerProfileReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Customer profile created successfully")
                    .setUserId(userId.toString())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(CreateCustomerProfileReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(CreateCustomerProfileReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while creating customer profile", ex);
            responseObserver.onNext(CreateCustomerProfileReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteCustomerProfile(
            DeleteCustomerProfileRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            userService.deleteCustomerProfileForBooking(userId);
            responseObserver.onNext(success("Customer profile deleted successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex.getErrorCode()));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while deleting customer profile", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    private OperationReply success(String message) {
        return OperationReply.newBuilder()
                .setSuccess(true)
                .setMessage(message)
                .build();
    }

    private java.time.LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return java.time.LocalDate.parse(value);
    }

    private com.cinema.Enum.UserEnum.Gender parseOptionalGender(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return com.cinema.Enum.UserEnum.Gender.valueOf(value);
    }

    private OperationReply failure(ErrorCode errorCode) {
        return OperationReply.newBuilder()
                .setSuccess(false)
                .setErrorKey(errorCode.name())
                .setMessage(errorCode.getMessage())
                .build();
    }
}
