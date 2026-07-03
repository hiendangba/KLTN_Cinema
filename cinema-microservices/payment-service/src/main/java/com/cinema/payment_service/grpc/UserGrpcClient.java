package com.cinema.payment_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.user.AdjustUserLoyaltyPointsReply;
import com.cinema.grpc.user.AdjustUserLoyaltyPointsRequest;
import com.cinema.grpc.user.GetUserBasicByIdReply;
import com.cinema.grpc.user.GetUserBasicByIdRequest;
import com.cinema.grpc.user.UserBasicPayload;
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

    public UserBasicInfo getUserBasicById(UUID userId) {
        try {
            GetUserBasicByIdReply reply = userBlockingStub.getUserBasicById(
                    GetUserBasicByIdRequest.newBuilder()
                            .setUserId(userId == null ? "" : userId.toString())
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
            if (!reply.hasUser()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return toUserBasicInfo(reply.getUser());
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public long addUserLoyaltyPoints(UUID userId, long loyaltyPoints) {
        return adjustUserLoyaltyPoints(userId, loyaltyPoints, true);
    }

    public long deductUserLoyaltyPoints(UUID userId, long loyaltyPoints) {
        return adjustUserLoyaltyPoints(userId, loyaltyPoints, false);
    }

    private long adjustUserLoyaltyPoints(UUID userId, long loyaltyPoints, boolean isAddition) {
        try {
            AdjustUserLoyaltyPointsReply reply = isAddition
                    ? userBlockingStub.addUserLoyaltyPoints(buildLoyaltyPointsRequest(userId, loyaltyPoints))
                    : userBlockingStub.deductUserLoyaltyPoints(buildLoyaltyPointsRequest(userId, loyaltyPoints));
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
            return reply.getLoyaltyPoints();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private AdjustUserLoyaltyPointsRequest buildLoyaltyPointsRequest(UUID userId, long loyaltyPoints) {
        return AdjustUserLoyaltyPointsRequest.newBuilder()
                .setUserId(userId == null ? "" : userId.toString())
                .setLoyaltyPoints(loyaltyPoints)
                .build();
    }

    private UserBasicInfo toUserBasicInfo(UserBasicPayload payload) {
        try {
            return new UserBasicInfo(
                    UUID.fromString(payload.getId()),
                    payload.getName(),
                    payload.getLoyaltyPoints());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    public record UserBasicInfo(UUID id, String name, long loyaltyPoints) {
    }
}
