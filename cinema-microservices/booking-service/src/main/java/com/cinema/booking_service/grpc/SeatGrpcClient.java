package com.cinema.booking_service.grpc;

import com.cinema.Enum.HallEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.seat.GetLayoutByHallIdReply;
import com.cinema.grpc.seat.GetLayoutByHallIdRequest;
import com.cinema.grpc.seat.GetSeatsByCodesReply;
import com.cinema.grpc.seat.GetSeatsByCodesRequest;
import com.cinema.grpc.seat.SeatInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class SeatGrpcClient {

    private final SeatInternalServiceGrpc.SeatInternalServiceBlockingStub seatBlockingStub;

    public SeatGrpcClient(GrpcChannelFactory channelFactory) {
        this.seatBlockingStub = SeatInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("seat"));
    }

    public Map<String, SeatSnapshot> getSeatSnapshotsByCodes(UUID hallId, Collection<String> seatCodes) {
        try {
            GetSeatsByCodesReply reply = seatBlockingStub.getSeatsByCodes(
                    GetSeatsByCodesRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .addAllSeatCodes(seatCodes)
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SEAT_SERVICE_ERROR));
            }

            Map<String, SeatSnapshot> result = new LinkedHashMap<>();
            reply.getSeatsList().forEach(seat -> {
                String normalizedSeatCode = seat.getSeatCode().trim().toUpperCase(Locale.ROOT);
                try {
                    UUID seatId = UUID.fromString(seat.getId().trim());
                    HallEnum.SeatType seatType = HallEnum.SeatType.valueOf(seat.getSeatType().trim().toUpperCase(Locale.ROOT));
                    result.put(normalizedSeatCode, new SeatSnapshot(seatId, seatType));
                } catch (RuntimeException ex) {
                    throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
                }
            });
            return result;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
        }
    }

    public LayoutBundle getLayoutByHallId(UUID hallId) {
        try {
            GetLayoutByHallIdReply reply = seatBlockingStub.getLayoutByHallId(
                    GetLayoutByHallIdRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .build());
            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SEAT_SERVICE_ERROR));
            }

            return LayoutBundle.builder()
                    .totalRows(reply.getProfile().getTotalRows())
                    .totalCols(reply.getProfile().getTotalCols())
                    .screenPosition(reply.getProfile().getScreenPosition())
                    .seats(reply.getSeatsList())
                    .cells(reply.getCellsList())
                    .build();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
        }
    }

    public record SeatSnapshot(UUID seatId, HallEnum.SeatType seatType) {
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
