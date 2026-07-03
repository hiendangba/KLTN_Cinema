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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(UserGrpcClient.class);
    private final UserInternalServiceGrpc.UserInternalServiceBlockingStub userBlockingStub;

    public UserGrpcClient(GrpcChannelFactory channelFactory) {
        this.userBlockingStub = UserInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("user"));
    }

    public UserBasicInfo getUserBasicById(UUID userId) {
        try {
            log.info("USER_GRPC_GET_BASIC_REQUEST userId={}", userId);
            GetUserBasicByIdReply reply = userBlockingStub.getUserBasicById(
                    GetUserBasicByIdRequest.newBuilder()
                            .setUserId(userId == null ? "" : userId.toString())
                            .build());
            if (!reply.getSuccess()) {
                log.warn(
                        "USER_GRPC_GET_BASIC_RESPONSE userId={} success=false errorKey={} message={}",
                        userId,
                        reply.getErrorKey(),
                        reply.getMessage());
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
            if (!reply.hasUser()) {
                log.warn("USER_GRPC_GET_BASIC_RESPONSE userId={} success=true hasUser=false", userId);
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            UserBasicInfo info = toUserBasicInfo(reply.getUser());
            log.info(
                    "USER_GRPC_GET_BASIC_RESPONSE userId={} success=true name={} loyaltyPoints={}",
                    userId,
                    info.name(),
                    info.loyaltyPoints());
            return info;
        } catch (StatusRuntimeException ex) {
            log.warn(
                    "USER_GRPC_GET_BASIC_STATUS_ERROR userId={} grpcCode={} description={}",
                    userId,
                    ex.getStatus().getCode(),
                    ex.getStatus().getDescription(),
                    ex);
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
            String action = isAddition ? "ADD" : "DEDUCT";
            log.info(
                    "USER_GRPC_LOYALTY_REQUEST action={} userId={} loyaltyPoints={}",
                    action,
                    userId,
                    loyaltyPoints);
            AdjustUserLoyaltyPointsReply reply = isAddition
                    ? userBlockingStub.addUserLoyaltyPoints(buildLoyaltyPointsRequest(userId, loyaltyPoints))
                    : userBlockingStub.deductUserLoyaltyPoints(buildLoyaltyPointsRequest(userId, loyaltyPoints));
            if (!reply.getSuccess()) {
                log.warn(
                        "USER_GRPC_LOYALTY_RESPONSE action={} userId={} success=false errorKey={} message={}",
                        action,
                        userId,
                        reply.getErrorKey(),
                        reply.getMessage());
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
            log.info(
                    "USER_GRPC_LOYALTY_RESPONSE action={} userId={} success=true nextPoints={}",
                    action,
                    userId,
                    reply.getLoyaltyPoints());
            return reply.getLoyaltyPoints();
        } catch (StatusRuntimeException ex) {
            log.warn(
                    "USER_GRPC_LOYALTY_STATUS_ERROR action={} userId={} loyaltyPoints={} grpcCode={} description={}",
                    isAddition ? "ADD" : "DEDUCT",
                    userId,
                    loyaltyPoints,
                    ex.getStatus().getCode(),
                    ex.getStatus().getDescription(),
                    ex);
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
