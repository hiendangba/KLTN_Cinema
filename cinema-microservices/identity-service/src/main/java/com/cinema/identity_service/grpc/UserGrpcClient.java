package com.cinema.identity_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.common.OperationReply;
import com.cinema.grpc.user.CreateCustomerProfileRequest;
import com.cinema.grpc.user.CreateManagerProfileRequest;
import com.cinema.grpc.user.CreateStaffProfileRequest;
import com.cinema.grpc.user.DeleteProfileRequest;
import com.cinema.grpc.user.DeleteCustomerProfileForBookingRequest;
import com.cinema.grpc.user.UserInternalServiceGrpc;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.RegisterManagerRequest;
import com.cinema.identity_service.dto.request.RegisterStaffRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserGrpcClient {

    private final UserInternalServiceGrpc.UserInternalServiceBlockingStub userBlockingStub;

    public UserGrpcClient(GrpcChannelFactory channelFactory) {
        this.userBlockingStub = UserInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("user"));
    }

    public void createCustomerProfile(RegisterCustomerRequest request) {
        try {
            OperationReply reply = userBlockingStub.createCustomerProfile(CreateCustomerProfileRequest.newBuilder()
                    .setId(request.getId().toString())
                    .setName(request.getName())
                    .setEmail(request.getEmail())
                    .setDob(request.getDob().toString())
                    .setGender(request.getGender().name())
                    .setPhone(request.getPhone())
                    .setRole(request.getRole().name())
                    .build());
            ensureSuccess(reply, ErrorCode.NOT_CREATED);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void createManagerProfile(RegisterManagerRequest request) {
        try {
            OperationReply reply = userBlockingStub.createManagerProfile(CreateManagerProfileRequest.newBuilder()
                    .setId(request.getId().toString())
                    .setName(request.getName())
                    .setEmail(request.getEmail())
                    .setDob(request.getDob().toString())
                    .setGender(request.getGender().name())
                    .setPhone(request.getPhone())
                    .setRole(request.getRole().name())
                    .setBankCode(nullToEmpty(request.getBankCode()))
                    .setAccountNumber(nullToEmpty(request.getAccountNumber()))
                    .setAccountName(nullToEmpty(request.getAccountName()))
                    .build());
            ensureSuccess(reply, ErrorCode.NOT_CREATED);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void createStaffProfile(RegisterStaffRequest request) {
        try {
            OperationReply reply = userBlockingStub.createStaffProfile(CreateStaffProfileRequest.newBuilder()
                    .setId(request.getId().toString())
                    .setName(request.getName())
                    .setEmail(request.getEmail())
                    .setDob(request.getDob().toString())
                    .setGender(request.getGender().name())
                    .setPhone(request.getPhone())
                    .setRole(request.getRole().name())
                    .setBankCode(nullToEmpty(request.getBankCode()))
                    .setAccountNumber(nullToEmpty(request.getAccountNumber()))
                    .setAccountName(nullToEmpty(request.getAccountName()))
                    .build());
            ensureSuccess(reply, ErrorCode.NOT_CREATED);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void deleteCustomerProfile(UUID userId, UUID actorId, String actorRole) {
        deleteProfile(userId, actorId, actorRole, DeleteAction.CUSTOMER);
    }

    public void deleteManagerProfile(UUID userId, UUID actorId, String actorRole) {
        deleteProfile(userId, actorId, actorRole, DeleteAction.MANAGER);
    }

    public void deleteStaffProfile(UUID userId, UUID actorId, String actorRole) {
        deleteProfile(userId, actorId, actorRole, DeleteAction.STAFF);
    }

    public void deleteCustomerProfileForBooking(UUID userId) {
        try {
            OperationReply reply = userBlockingStub.deleteCustomerProfileForBooking(
                    DeleteCustomerProfileForBookingRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());
            ensureSuccess(reply, ErrorCode.NOT_CREATED);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private void ensureSuccess(OperationReply reply, ErrorCode fallback) {
        if (!reply.getSuccess()) {
            throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), fallback));
        }
    }

    private void deleteProfile(UUID userId, UUID actorId, String actorRole, DeleteAction action) {
        try {
            OperationReply reply = switch (action) {
                case CUSTOMER -> userBlockingStub.deleteCustomerProfile(buildDeleteRequest(userId, actorId, actorRole));
                case MANAGER -> userBlockingStub.deleteManagerProfile(buildDeleteRequest(userId, actorId, actorRole));
                case STAFF -> userBlockingStub.deleteStaffProfile(buildDeleteRequest(userId, actorId, actorRole));
            };
            ensureSuccess(reply, ErrorCode.NOT_CREATED);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private DeleteProfileRequest buildDeleteRequest(UUID userId, UUID actorId, String actorRole) {
        return DeleteProfileRequest.newBuilder()
                .setUserId(userId.toString())
                .setActorId(actorId.toString())
                .setActorRole(actorRole == null ? "" : actorRole)
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private enum DeleteAction {
        CUSTOMER,
        MANAGER,
        STAFF
    }
}
