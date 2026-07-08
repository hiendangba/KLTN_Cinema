package com.cinema.user_service.grpc;

import com.cinema.grpc.user.GetUserBasicByIdReply;
import com.cinema.grpc.user.GetUserBasicByIdRequest;
import com.cinema.grpc.user.GetCustomerRankByIdRequest;
import com.cinema.user_service.dto.response.CustomerRankResponse;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.services.CustomerRankService;
import com.cinema.user_service.services.UserService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInternalGrpcServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private CustomerRankService customerRankService;

    @Test
    void getUserBasicById_shouldIncludeCustomerRankLevel() {
        UUID userId = UUID.randomUUID();
        UserInternalGrpcService grpcService = new UserInternalGrpcService(userService, customerRankService);
        CapturingObserver observer = new CapturingObserver();

        when(userService.getUserById(userId)).thenReturn(UserResponse.builder()
                .id(userId)
                .name("Customer")
                .loyaltyPoints(123L)
                .lifetimePaidAmount(BigDecimal.valueOf(500000))
                .customerRank(CustomerRankResponse.builder()
                        .code("GOLD")
                        .name("Gold")
                        .level(3)
                        .earningAmountUnit(BigDecimal.valueOf(1000))
                        .earningPointsPerUnit(BigDecimal.valueOf(2))
                        .build())
                .build());

        grpcService.getUserBasicById(
                GetUserBasicByIdRequest.newBuilder()
                        .setUserId(userId.toString())
                        .build(),
                observer);

        assertEquals(1, observer.replyCount);
        assertEquals(3, observer.reply.getUser().getCustomerRankLevel());
    }

    @Test
    void getCustomerRankById_shouldReturnRankSnapshot() {
        UUID rankId = UUID.randomUUID();
        UserInternalGrpcService grpcService = new UserInternalGrpcService(userService, customerRankService);
        CapturingRankObserver observer = new CapturingRankObserver();

        when(customerRankService.getRankById(rankId)).thenReturn(CustomerRankResponse.builder()
                .id(rankId)
                .code("GOLD")
                .name("Gold")
                .minLifetimeAmount(BigDecimal.valueOf(1000000))
                .earningAmountUnit(BigDecimal.valueOf(1000))
                .earningPointsPerUnit(BigDecimal.valueOf(2))
                .level(3)
                .build());

        grpcService.getCustomerRankById(
                GetCustomerRankByIdRequest.newBuilder()
                        .setRankId(rankId.toString())
                        .build(),
                observer);

        assertEquals(1, observer.replyCount);
        assertEquals(rankId.toString(), observer.reply.getRank().getId());
        assertEquals("GOLD", observer.reply.getRank().getCode());
        assertEquals("Gold", observer.reply.getRank().getName());
        assertEquals("1000000", observer.reply.getRank().getMinLifetimeAmount());
        assertEquals(3, observer.reply.getRank().getLevel());
    }

    private static final class CapturingObserver implements StreamObserver<GetUserBasicByIdReply> {
        private GetUserBasicByIdReply reply;
        private int replyCount;

        @Override
        public void onNext(GetUserBasicByIdReply value) {
            this.reply = value;
            this.replyCount++;
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onCompleted() {
        }
    }

    private static final class CapturingRankObserver implements StreamObserver<com.cinema.grpc.user.GetCustomerRankByIdReply> {
        private com.cinema.grpc.user.GetCustomerRankByIdReply reply;
        private int replyCount;

        @Override
        public void onNext(com.cinema.grpc.user.GetCustomerRankByIdReply value) {
            this.reply = value;
            this.replyCount++;
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onCompleted() {
        }
    }
}
