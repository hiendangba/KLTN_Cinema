package com.cinema.hall_service.grpc;

import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.Enum.HallEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.GrpcErrorUtils;
import com.cinema.grpc.seat.CreateLayoutDefinitionReply;
import com.cinema.grpc.seat.CreateLayoutDefinitionRequest;
import com.cinema.grpc.seat.GetLayoutByHallIdReply;
import com.cinema.grpc.seat.GetLayoutByHallIdRequest;
import com.cinema.grpc.seat.LayoutDefinitionCellInput;
import com.cinema.grpc.seat.ReplaceLayoutDefinitionReply;
import com.cinema.grpc.seat.ReplaceLayoutDefinitionRequest;
import com.cinema.grpc.seat.SeatInternalServiceGrpc;
import com.cinema.hall_service.dto.request.HallLayoutDefinitionRequest;
import com.cinema.hall_service.dto.response.SeatResponse;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class SeatGrpcClient {

    private final SeatInternalServiceGrpc.SeatInternalServiceBlockingStub seatBlockingStub;

    public SeatGrpcClient(GrpcChannelFactory channelFactory) {
        this.seatBlockingStub = SeatInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("seat"));
    }

    public ActionMessageResponse createLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request) {
        try {
            CreateLayoutDefinitionReply reply = seatBlockingStub.createLayoutDefinition(
                    CreateLayoutDefinitionRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .setTotalRows(request.getTotalRows())
                            .setTotalCols(request.getTotalCols())
                            .setScreenPosition(request.getScreenPosition().name())
                            .addAllCells(request.getCells().stream().map(this::toCellInput).toList())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SEAT_SERVICE_ERROR));
            }
            return ActionMessageResponse.builder().message(reply.getMessage()).build();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
        }
    }

    public ActionMessageResponse replaceLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request) {
        try {
            ReplaceLayoutDefinitionReply reply = seatBlockingStub.replaceLayoutDefinition(
                    ReplaceLayoutDefinitionRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .setTotalRows(request.getTotalRows())
                            .setTotalCols(request.getTotalCols())
                            .setScreenPosition(request.getScreenPosition().name())
                            .addAllCells(request.getCells().stream().map(this::toCellInput).toList())
                            .build());

            if (!reply.getSuccess()) {
                throw new BusinessException(GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SEAT_SERVICE_ERROR));
            }
            return ActionMessageResponse.builder().message(reply.getMessage()).build();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
        }
    }

    public List<SeatResponse> getHallSeats(UUID hallId) {
        try {
            GetLayoutByHallIdReply reply = seatBlockingStub.getLayoutByHallId(
                    GetLayoutByHallIdRequest.newBuilder()
                            .setHallId(hallId.toString())
                            .build());

            if (!reply.getSuccess()) {
                ErrorCode errorCode = GrpcErrorUtils.resolve(reply.getErrorKey(), ErrorCode.SEAT_SERVICE_ERROR);
                if (errorCode == ErrorCode.NOT_FOUND) {
                    return List.of();
                }
                throw new BusinessException(errorCode);
            }

            return reply.getSeatsList().stream()
                    .map(seat -> SeatResponse.builder()
                            .id(UUID.fromString(seat.getId()))
                            .seatCode(seat.getSeatCode())
                            .row(seat.getRow())
                            .col(seat.getCol())
                            .seatType(HallEnum.SeatType.valueOf(seat.getSeatType().trim().toUpperCase(Locale.ROOT)))
                            .build())
                    .toList();
        } catch (StatusRuntimeException ex) {
            throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.SEAT_SERVICE_ERROR);
        }
    }

    private LayoutDefinitionCellInput toCellInput(HallLayoutDefinitionRequest.CellInput cell) {
        LayoutDefinitionCellInput.Builder builder = LayoutDefinitionCellInput.newBuilder()
                .setRow(cell.getRow())
                .setCol(cell.getCol())
                .setType(cell.getType().name());
        if (cell.getSeatType() != null) {
            builder.setSeatType(cell.getSeatType().name());
        }
        return builder.build();
    }
}
