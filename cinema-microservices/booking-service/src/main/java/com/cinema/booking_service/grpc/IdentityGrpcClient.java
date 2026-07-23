package com.cinema.booking_service.grpc;

import com.cinema.Enum.UserEnum;
import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.identity.GetUserAccountByUserIdReply;
import com.cinema.grpc.identity.GetUserAccountByUserIdRequest;
import com.cinema.grpc.identity.CreateCustomerProfileReply;
import com.cinema.grpc.identity.CreateCustomerProfileRequest;
import com.cinema.grpc.identity.DeleteCustomerProfileRequest;
import com.cinema.grpc.identity.IdentityInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class IdentityGrpcClient {

    private final IdentityInternalServiceGrpc.IdentityInternalServiceBlockingStub identityBlockingStub;

    public IdentityGrpcClient(GrpcChannelFactory channelFactory) {
        this.identityBlockingStub = IdentityInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("identity"));
    }

    public UUID createCustomer(CreateBookingRequest.CustomerInfo customerInfo) {
        try {
            CreateCustomerProfileReply reply = identityBlockingStub.createCustomerProfile(
                    CreateCustomerProfileRequest.newBuilder()
                            .setName(normalize(customerInfo == null ? null : customerInfo.getFullName()))
                            .setEmail(normalize(customerInfo == null ? null : customerInfo.getEmail()))
                            .setPhone(normalize(customerInfo == null ? null : customerInfo.getPhone()))
                            .setDob(LocalDate.of(1900, 1, 1).toString())
                            .setGender(UserEnum.Gender.OTHER.name())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.NOT_CREATED));
            }

            if (reply.getUserId() == null || reply.getUserId().isBlank()) {
                throw new BusinessException(ErrorCode.NOT_CREATED);
            }

            return UUID.fromString(reply.getUserId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void deleteCustomer(UUID userId) {
        try {
            com.cinema.grpc.common.OperationReply reply = identityBlockingStub.deleteCustomerProfile(
                    DeleteCustomerProfileRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.NOT_CREATED));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public void requireCustomerExists(UUID customerId) {
        try {
            GetUserAccountByUserIdReply reply = identityBlockingStub.getUserAccountByUserId(
                    GetUserAccountByUserIdRequest.newBuilder()
                            .setUserId(customerId.toString())
                            .build());

            if (!reply.getSuccess() || !reply.hasAccount() || reply.getAccount() == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }

            String role = reply.getAccount().getRole();
            if (role == null || !UserEnum.UserRole.CUSTOMER.name().equals(role)) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
