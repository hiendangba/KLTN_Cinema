package cinema.seat_service.grpc;

import cinema.seat_service.dto.request.PutHallLayoutDefinitionRequest;
import cinema.seat_service.dto.response.HallLayoutDefinitionResponse;
import cinema.seat_service.enums.ScreenPosition;
import cinema.seat_service.enums.SeatType;
import cinema.seat_service.service.SeatLayoutService;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.grpc.seat.CreateLayoutDefinitionReply;
import com.cinema.grpc.seat.CreateLayoutDefinitionRequest;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.grpc.seat.GetLayoutByHallIdReply;
import com.cinema.grpc.seat.GetLayoutByHallIdRequest;
import com.cinema.grpc.seat.GetSeatsByCodesReply;
import com.cinema.grpc.seat.GetSeatsByCodesRequest;
import com.cinema.grpc.seat.LayoutCellPayload;
import com.cinema.grpc.seat.LayoutProfilePayload;
import com.cinema.grpc.seat.LayoutSeatPayload;
import com.cinema.grpc.seat.ReplaceLayoutDefinitionReply;
import com.cinema.grpc.seat.ReplaceLayoutDefinitionRequest;
import com.cinema.grpc.seat.SeatInternalServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatInternalGrpcService extends SeatInternalServiceGrpc.SeatInternalServiceImplBase {

    private final SeatLayoutService seatLayoutService;

    @Override
    public void getLayoutByHallId(GetLayoutByHallIdRequest request, StreamObserver<GetLayoutByHallIdReply> responseObserver) {
        UUID hallId;
        try {
            hallId = UUID.fromString(request.getHallId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetLayoutByHallIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            HallLayoutDefinitionResponse layout = seatLayoutService.getHallLayoutDefinition(hallId);
            GetLayoutByHallIdReply.Builder builder = GetLayoutByHallIdReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Layout fetched successfully")
                    .setProfile(LayoutProfilePayload.newBuilder()
                            .setHallId(layout.getHallId().toString())
                            .setTotalRows(layout.getTotalRows())
                            .setTotalCols(layout.getTotalCols())
                            .setScreenPosition(layout.getScreenPosition().name())
                            .build());

            layout.getSeats().forEach(seat -> builder.addSeats(LayoutSeatPayload.newBuilder()
                    .setId(seat.getId().toString())
                    .setHallId(layout.getHallId().toString())
                    .setSeatCode(seat.getSeatCode())
                    .setRow(seat.getRow())
                    .setCol(seat.getCol())
                    .setSeatType(seat.getSeatType().name())
                    .build()));

            layout.getCells().forEach(cell -> builder.addCells(LayoutCellPayload.newBuilder()
                    .setId(cell.getId().toString())
                    .setHallId(layout.getHallId().toString())
                    .setRow(cell.getRow())
                    .setCol(cell.getCol())
                    .setCellType(cell.getCellType().name())
                    .build()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetLayoutByHallIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching layout", ex);
            responseObserver.onNext(GetLayoutByHallIdReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getSeatsByCodes(GetSeatsByCodesRequest request, StreamObserver<GetSeatsByCodesReply> responseObserver) {
        UUID hallId;
        try {
            hallId = UUID.fromString(request.getHallId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(GetSeatsByCodesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
            return;
        }

        try {
            GetSeatsByCodesReply.Builder builder = GetSeatsByCodesReply.newBuilder()
                    .setSuccess(true)
                    .setMessage("Seats fetched successfully");

            seatLayoutService.getSeatsByCodes(hallId, request.getSeatCodesList()).forEach(seat -> builder.addSeats(
                    LayoutSeatPayload.newBuilder()
                            .setId(seat.getId().toString())
                            .setHallId(seat.getHallId().toString())
                            .setSeatCode(seat.getSeatCode())
                            .setRow(seat.getRow())
                            .setCol(seat.getCol())
                            .setSeatType(seat.getSeatType().name())
                            .build()
            ));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(GetSeatsByCodesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while fetching seats by code", ex);
            responseObserver.onNext(GetSeatsByCodesReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void createLayoutDefinition(CreateLayoutDefinitionRequest request,
                                       StreamObserver<CreateLayoutDefinitionReply> responseObserver) {
        try {
            UUID hallId = UUID.fromString(request.getHallId());
            PutHallLayoutDefinitionRequest payload = toPutRequest(
                    request.getTotalRows(),
                    request.getTotalCols(),
                    request.getScreenPosition(),
                    request.getCellsList());
            ActionMessageResponse result = seatLayoutService.createHallLayoutDefinition(hallId, payload);
            responseObserver.onNext(CreateLayoutDefinitionReply.newBuilder()
                    .setSuccess(true)
                    .setMessage(result.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(CreateLayoutDefinitionReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(CreateLayoutDefinitionReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while creating layout definition", ex);
            responseObserver.onNext(CreateLayoutDefinitionReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void replaceLayoutDefinition(ReplaceLayoutDefinitionRequest request,
                                        StreamObserver<ReplaceLayoutDefinitionReply> responseObserver) {
        try {
            UUID hallId = UUID.fromString(request.getHallId());
            PutHallLayoutDefinitionRequest payload = toPutRequest(
                    request.getTotalRows(),
                    request.getTotalCols(),
                    request.getScreenPosition(),
                    request.getCellsList());
            ActionMessageResponse result = seatLayoutService.replaceHallLayoutDefinition(hallId, payload);
            responseObserver.onNext(ReplaceLayoutDefinitionReply.newBuilder()
                    .setSuccess(true)
                    .setMessage(result.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onNext(ReplaceLayoutDefinitionReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INVALID_FORMAT.name())
                    .setMessage(ErrorCode.INVALID_FORMAT.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException ex) {
            responseObserver.onNext(ReplaceLayoutDefinitionReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ex.getErrorCode().name())
                    .setMessage(ex.getMessage())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("Unexpected gRPC error while replacing layout definition", ex);
            responseObserver.onNext(ReplaceLayoutDefinitionReply.newBuilder()
                    .setSuccess(false)
                    .setErrorKey(ErrorCode.INTERNAL_ERROR.name())
                    .setMessage(ErrorCode.INTERNAL_ERROR.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private PutHallLayoutDefinitionRequest toPutRequest(
            int totalRows,
            int totalCols,
            String screenPosition,
            java.util.List<com.cinema.grpc.seat.LayoutDefinitionCellInput> cells) {
        PutHallLayoutDefinitionRequest request = new PutHallLayoutDefinitionRequest();
        request.setTotalRows(totalRows);
        request.setTotalCols(totalCols);
        request.setScreenPosition(ScreenPosition.valueOf(screenPosition.trim().toUpperCase()));

        java.util.List<PutHallLayoutDefinitionRequest.CellInput> convertedCells = new java.util.ArrayList<>();
        for (com.cinema.grpc.seat.LayoutDefinitionCellInput cell : cells) {
            PutHallLayoutDefinitionRequest.CellInput converted = new PutHallLayoutDefinitionRequest.CellInput();
            converted.setRow(cell.getRow());
            converted.setCol(cell.getCol());
            converted.setType(PutHallLayoutDefinitionRequest.CellInputType.valueOf(cell.getType().trim().toUpperCase()));
            if (cell.getSeatType() != null && !cell.getSeatType().isBlank()) {
                converted.setSeatType(SeatType.valueOf(cell.getSeatType().trim().toUpperCase()));
            }
            convertedCells.add(converted);
        }
        request.setCells(convertedCells);
        return request;
    }
}
