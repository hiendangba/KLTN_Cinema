package com.cinema.review_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.user.GetUserBasicByIdReply;
import com.cinema.grpc.user.GetUserBasicByIdRequest;
import com.cinema.grpc.user.UserInternalServiceGrpc;
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

    public String getUserNameById(UUID userId) {
        try {
            GetUserBasicByIdReply reply = userBlockingStub.getUserBasicById(
                    GetUserBasicByIdRequest.newBuilder()
                            .setUserId(userId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }

            if (!reply.hasUser()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }

            String name = reply.getUser().getName();
            return name == null || name.isBlank() ? null : name;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }
}
