package com.cinema.user_service.grpc;

import com.cinema.Enum.UserEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.common.OperationReply;
import com.cinema.grpc.user.CheckUserExistsReply;
import com.cinema.grpc.user.CheckUserExistsRequest;
import com.cinema.grpc.user.DeleteProfileRequest;
import com.cinema.grpc.user.DeleteCustomerProfileForBookingRequest;
import com.cinema.grpc.user.CreateCustomerProfileRequest;
import com.cinema.grpc.user.CreateManagerProfileRequest;
import com.cinema.grpc.user.CreateStaffProfileRequest;
import com.cinema.grpc.user.GetUserBasicByIdReply;
import com.cinema.grpc.user.GetUserBasicByIdRequest;
import com.cinema.grpc.user.UserBasicPayload;
import com.cinema.grpc.user.UserInternalServiceGrpc;
import com.cinema.user_service.dto.request.RegisterCustomerRequest;
import com.cinema.user_service.dto.request.RegisterManagerRequest;
import com.cinema.user_service.dto.request.RegisterStaffRequest;
import com.cinema.user_service.dto.response.UserExistenceResponse;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.services.UserService;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserInternalGrpcService extends UserInternalServiceGrpc.UserInternalServiceImplBase
        implements BindableService {

    private final UserService userService;

    @Override
    public void createCustomerProfile(
            CreateCustomerProfileRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            userService.createCustomerProfile(RegisterCustomerRequest.builder()
                    .id(UUID.fromString(request.getId()))
                    .name(request.getName())
                    .email(request.getEmail())
                    .dob(LocalDate.parse(request.getDob()))
                    .gender(UserEnum.Gender.valueOf(request.getGender()))
                    .phone(request.getPhone())
                    .role(UserEnum.UserRole.valueOf(request.getRole()))
                    .build());
            responseObserver.onNext(success("Customer profile created successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while creating customer profile", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void createManagerProfile(
            CreateManagerProfileRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            userService.createManagerProfile(RegisterManagerRequest.builder()
                    .id(UUID.fromString(request.getId()))
                    .name(request.getName())
                    .email(request.getEmail())
                    .dob(LocalDate.parse(request.getDob()))
                    .gender(UserEnum.Gender.valueOf(request.getGender()))
                    .phone(request.getPhone())
                    .role(UserEnum.UserRole.valueOf(request.getRole()))
                    .bankCode(blankToNull(request.getBankCode()))
                    .accountNumber(blankToNull(request.getAccountNumber()))
                    .accountName(blankToNull(request.getAccountName()))
                    .build());
            responseObserver.onNext(success("Manager profile created successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while creating manager profile", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void createStaffProfile(
            CreateStaffProfileRequest request,
            StreamObserver<OperationReply> responseObserver) {
        try {
            userService.createStaffProfile(RegisterStaffRequest.builder()
                    .id(UUID.fromString(request.getId()))
                    .name(request.getName())
                    .email(request.getEmail())
                    .dob(LocalDate.parse(request.getDob()))
                    .gender(UserEnum.Gender.valueOf(request.getGender()))
                    .phone(request.getPhone())
                    .role(UserEnum.UserRole.valueOf(request.getRole()))
                    .bankCode(blankToNull(request.getBankCode()))
                    .accountNumber(blankToNull(request.getAccountNumber()))
                    .accountName(blankToNull(request.getAccountName()))
                    .build());
            responseObserver.onNext(success("Staff profile created successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while creating staff profile", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteCustomerProfile(DeleteProfileRequest request, StreamObserver<OperationReply> responseObserver) {
        handleDelete(request, responseObserver, UserEnum.UserRole.CUSTOMER);
    }

    @Override
    public void deleteCustomerProfileForBooking(
            DeleteCustomerProfileForBookingRequest request,
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
            responseObserver.onNext(failure(ex));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while deleting booking customer profile", ex);
            responseObserver.onNext(failure(ErrorCode.INTERNAL_ERROR));
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteManagerProfile(DeleteProfileRequest request, StreamObserver<OperationReply> responseObserver) {
        handleDelete(request, responseObserver, UserEnum.UserRole.MANAGER);
    }

    @Override
    public void deleteStaffProfile(DeleteProfileRequest request, StreamObserver<OperationReply> responseObserver) {
        handleDelete(request, responseObserver, UserEnum.UserRole.STAFF);
    }

    @Override
    public void checkUserExists(
            CheckUserExistsRequest request,
            StreamObserver<CheckUserExistsReply> responseObserver) {
        try {
            UserExistenceResponse response = userService.checkUserExists(UUID.fromString(request.getUserId()));
            responseObserver.onNext(CheckUserExistsReply.newBuilder()
                    .setSuccess(true)
                    .setExists(response.isExists())
                    .setMessage(response.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(CheckUserExistsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(CheckUserExistsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while checking user existence", ex);
            responseObserver.onNext(CheckUserExistsReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getUserBasicById(GetUserBasicByIdRequest request,
                                 StreamObserver<GetUserBasicByIdReply> responseObserver) {
        try {
            UserResponse user = userService.getUserById(UUID.fromString(request.getUserId()));
            responseObserver.onNext(GetUserBasicByIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("User fetched successfully")
                    .setUser(UserBasicPayload.newBuilder()
                            .setId(user.getId().toString())
                            .setName(blankToEmpty(user.getName()))
                            .build())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetUserBasicByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetUserBasicByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching user by id", ex);
            responseObserver.onNext(GetUserBasicByIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private void handleDelete(
            DeleteProfileRequest request,
            StreamObserver<OperationReply> responseObserver,
            UserEnum.UserRole targetRole) {
        try {
            UUID targetUserId = UUID.fromString(request.getUserId());
            UUID actorId = UUID.fromString(request.getActorId());
            UserEnum.UserRole actorRole = UserEnum.UserRole.valueOf(request.getActorRole());

            switch (targetRole) {
                case CUSTOMER -> userService.deleteCustomerProfile(targetUserId, actorId, actorRole);
                case MANAGER -> userService.deleteManagerProfile(targetUserId, actorId, actorRole);
                case STAFF -> userService.deleteStaffProfile(targetUserId, actorId, actorRole);
                default -> throw new BusinessException(ErrorCode.INVALID_FORMAT);
            }

            responseObserver.onNext(success("Profile deleted successfully"));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(failure(ErrorCode.INVALID_FORMAT));
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(failure(ex));
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while deleting user profile", ex);
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

    private OperationReply failure(BusinessException exception) {
        return failure(exception.getErrorCode());
    }

    private OperationReply failure(ErrorCode errorCode) {
        return OperationReply.newBuilder()
                .setSuccess(false)
                .setErrorKey(errorCode.name())
                .setMessage(errorCode.getMessage())
                .build();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value;
    }
}
