package com.cinema.showtime_service.grpc;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.seat.GetLayoutByHallIdReply;
import com.cinema.grpc.seat.GetLayoutByHallIdRequest;
import com.cinema.grpc.seat.SeatInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SeatGrpcClient {

    private final SeatInternalServiceGrpc.SeatInternalServiceBlockingStub seatBlockingStub;

    public SeatGrpcClient(GrpcChannelFactory channelFactory) {
        this.seatBlockingStub = SeatInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("seat"));
    }

    public LayoutBundle getLayoutByHallId(UUID hallId) {
        try {
            GetLayoutByHallIdReply reply = seatBlockingStub.getLayoutByHallId(
                    GetLayoutByHallIdRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.EXTERNAL_SERVICE_ERROR));
            }

            return LayoutBundle.builder()
                    .totalRows(reply.getProfile().getTotalRows())
                    .totalCols(reply.getProfile().getTotalCols())
                    .screenPosition(reply.getProfile().getScreenPosition())
                    .seats(reply.getSeatsList())
                    .cells(reply.getCellsList())
                    .build();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    @Getter
    @Builder
    public static class LayoutBundle {
        private Integer totalRows;
        private Integer totalCols;
        private String screenPosition;
        private List<com.cinema.grpc.seat.LayoutSeatPayload> seats;
        private List<com.cinema.grpc.seat.LayoutCellPayload> cells;
    }
}
