package com.cinema.user_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.common.OperationReply;
import com.cinema.grpc.identity.IdentityInternalServiceGrpc;
import com.cinema.grpc.identity.LockUserAccountRequest;
import com.cinema.grpc.identity.UnlockUserAccountRequest;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

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
}
