package com.cinema.user_service.grpc;

import com.cinema.grpc.user.GetUserBasicByIdReply;
import com.cinema.grpc.user.GetUserBasicByIdRequest;
import com.cinema.user_service.dto.response.CustomerRankResponse;
import com.cinema.user_service.dto.response.UserResponse;
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

    @Test
    void getUserBasicById_shouldIncludeCustomerRankLevel() {
        UUID userId = UUID.randomUUID();
        UserInternalGrpcService grpcService = new UserInternalGrpcService(userService);
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
}
