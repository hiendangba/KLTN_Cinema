package com.cinema.payment_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.user.GetCustomerRankByIdReply;
import com.cinema.grpc.user.GetCustomerRankByIdRequest;
import com.cinema.grpc.user.CustomerRankPayload;
import com.cinema.grpc.user.UserInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class CustomerRankGrpcClient {

    private final UserInternalServiceGrpc.UserInternalServiceBlockingStub userBlockingStub;

    public CustomerRankGrpcClient(GrpcChannelFactory channelFactory) {
        this.userBlockingStub = UserInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("user"));
    }

    public CustomerRankInfo getCustomerRankById(UUID rankId) {
        try {
            GetCustomerRankByIdReply reply = userBlockingStub.getCustomerRankById(
                    GetCustomerRankByIdRequest.newBuilder()
                            .setRankId(rankId == null ? "" : rankId.toString())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }
            if (!reply.hasRank()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }
            return toCustomerRankInfo(reply.getRank());
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private CustomerRankInfo toCustomerRankInfo(CustomerRankPayload payload) {
        try {
            return new CustomerRankInfo(
                    UUID.fromString(payload.getId()),
                    payload.getCode(),
                    payload.getName(),
                    parseAmount(payload.getMinLifetimeAmount()),
                    parseAmount(payload.getEarningAmountUnit()),
                    parseAmount(payload.getEarningPointsPerUnit()),
                    payload.getLevel(),
                    payload.getStatus());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    private BigDecimal parseAmount(String value) {
        try {
            return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    public record CustomerRankInfo(
            UUID id,
            String code,
            String name,
            BigDecimal minLifetimeAmount,
            BigDecimal earningAmountUnit,
            BigDecimal earningPointsPerUnit,
            Integer level,
            String status) {
    }
}
