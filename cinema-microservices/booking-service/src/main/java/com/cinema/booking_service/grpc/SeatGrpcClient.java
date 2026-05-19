package com.cinema.booking_service.grpc;

import com.cinema.Enum.HallEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.seat.GetSeatsByCodesReply;
import com.cinema.grpc.seat.GetSeatsByCodesRequest;
import com.cinema.grpc.seat.SeatInternalServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
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

    public Map<String, HallEnum.SeatType> getSeatTypesByCodes(UUID hallId, Collection<String> seatCodes) {
        try {
            GetSeatsByCodesReply reply = seatBlockingStub.getSeatsByCodes(
                    GetSeatsByCodesRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .addAllSeatCodes(seatCodes)
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SEAT_SERVICE_ERROR));
            }

            Map<String, HallEnum.SeatType> result = new LinkedHashMap<>();
            reply.getSeatsList().forEach(seat -> {
                String normalizedSeatCode = seat.getSeatCode().trim().toUpperCase(Locale.ROOT);
                try {
                    HallEnum.SeatType seatType = HallEnum.SeatType.valueOf(seat.getSeatType().trim().toUpperCase(Locale.ROOT));
                    result.put(normalizedSeatCode, seatType);
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
                }
            });
            return result;
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
        }
    }
}
