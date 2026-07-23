package com.cinema.payment_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.user.GetUserBasicByIdReply;
import com.cinema.grpc.user.GetUserBasicByIdRequest;
import com.cinema.grpc.user.UserBasicPayload;
import com.cinema.grpc.user.UserInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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

    private UserBasicInfo toUserBasicInfo(UserBasicPayload payload) {
        try {
            return new UserBasicInfo(
                    UUID.fromString(payload.getId()),
                    payload.getName(),
                    payload.getLoyaltyPoints(),
                    parseAmount(payload.getLifetimePaidAmount(), BigDecimal.ZERO),
                    payload.getCustomerRankCode(),
                    payload.getCustomerRankName(),
                    payload.getCustomerRankLevel(),
                    parseAmount(payload.getEarningAmountUnit(), BigDecimal.valueOf(1000L)),
                    parseAmount(payload.getEarningPointsPerUnit(), BigDecimal.ONE));
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private BigDecimal parseAmount(String value, BigDecimal fallback) {
        try {
            return value == null || value.isBlank() ? fallback : new BigDecimal(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    public record UserBasicInfo(
            UUID id,
            String name,
            long loyaltyPoints,
            BigDecimal lifetimePaidAmount,
            String customerRankCode,
            String customerRankName,
            Integer customerRankLevel,
            BigDecimal earningAmountUnit,
            BigDecimal earningPointsPerUnit) {
    }
}
