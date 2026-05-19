package cinema.seat_service.service.impl;

import cinema.seat_service.dto.request.PutHallLayoutDefinitionRequest;
import cinema.seat_service.dto.response.HallLayoutDefinitionResponse;
import cinema.seat_service.dto.response.LayoutCellResponse;
import cinema.seat_service.dto.response.LayoutSeatResponse;
import cinema.seat_service.entity.HallLayoutCell;
import cinema.seat_service.entity.HallLayoutProfile;
import cinema.seat_service.entity.OutboxEvent;
import cinema.seat_service.entity.Seat;
import cinema.seat_service.enums.LayoutCellType;
import cinema.seat_service.repository.HallLayoutCellRepository;
import cinema.seat_service.repository.HallLayoutProfileRepository;
import cinema.seat_service.repository.OutboxEventRepository;
import cinema.seat_service.repository.SeatRepository;
import cinema.seat_service.service.SeatLayoutService;
import cinema.seat_service.util.SeatCodeGenerator;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatLayoutServiceImpl implements SeatLayoutService {

    private final HallLayoutProfileRepository hallLayoutProfileRepository;
    private final SeatRepository seatRepository;
    private final HallLayoutCellRepository hallLayoutCellRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public ActionMessageResponse putHallLayoutDefinition(UUID hallId, PutHallLayoutDefinitionRequest request) {
        validateDefinition(request);

        HallLayoutProfile profile = hallLayoutProfileRepository.findById(hallId)
                .orElseGet(HallLayoutProfile::new);
        profile.setHallId(hallId);
        profile.setTotalRows(request.getTotalRows());
        profile.setTotalCols(request.getTotalCols());
        profile.setScreenPosition(request.getScreenPosition());
        profile.setIsDeleted(false);
        hallLayoutProfileRepository.save(profile);

        seatRepository.deleteAllByHallId(hallId);
        hallLayoutCellRepository.deleteAllByHallId(hallId);

        List<Seat> seats = new ArrayList<>();
        List<HallLayoutCell> cells = new ArrayList<>();
        for (PutHallLayoutDefinitionRequest.CellInput input : request.getCells()) {
            if (input.getType() == PutHallLayoutDefinitionRequest.CellInputType.SEAT) {
                Seat seat = new Seat();
                seat.setHallId(hallId);
                seat.setRow(input.getRow());
                seat.setCol(input.getCol());
                seat.setSeatType(input.getSeatType());
                seat.setSeatCode(SeatCodeGenerator.fromRowCol(input.getRow(), input.getCol()).toUpperCase(Locale.ROOT));
                seat.setIsDeleted(false);
                seats.add(seat);
                continue;
            }

            HallLayoutCell cell = new HallLayoutCell();
            cell.setHallId(hallId);
            cell.setRow(input.getRow());
            cell.setCol(input.getCol());
            cell.setCellType(input.getType() == PutHallLayoutDefinitionRequest.CellInputType.AISLE
                    ? LayoutCellType.AISLE : LayoutCellType.BLOCKED);
            cell.setIsDeleted(false);
            cells.add(cell);
        }

        seatRepository.saveAll(seats);
        hallLayoutCellRepository.saveAll(cells);
        publishOutboxEvent(hallId, seats.size(), cells.size());

        return ActionMessageResponse.builder()
                .message("Hall layout definition updated successfully")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HallLayoutDefinitionResponse getHallLayoutDefinition(UUID hallId) {
        HallLayoutProfile profile = hallLayoutProfileRepository.findByHallIdAndIsDeletedFalse(hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        List<LayoutSeatResponse> seats = seatRepository.findAllByHallIdAndIsDeletedFalseOrderByRowAscColAsc(hallId)
                .stream()
                .map(seat -> LayoutSeatResponse.builder()
                        .id(seat.getId())
                        .seatCode(seat.getSeatCode())
                        .row(seat.getRow())
                        .col(seat.getCol())
                        .seatType(seat.getSeatType())
                        .build())
                .toList();

        List<LayoutCellResponse> cells = hallLayoutCellRepository.findAllByHallIdAndIsDeletedFalseOrderByRowAscColAsc(hallId)
                .stream()
                .map(cell -> LayoutCellResponse.builder()
                        .id(cell.getId())
                        .row(cell.getRow())
                        .col(cell.getCol())
                        .cellType(cell.getCellType())
                        .build())
                .toList();

        return HallLayoutDefinitionResponse.builder()
                .hallId(hallId)
                .totalRows(profile.getTotalRows())
                .totalCols(profile.getTotalCols())
                .screenPosition(profile.getScreenPosition())
                .seats(seats)
                .cells(cells)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Seat> getSeatsByCodes(UUID hallId, Collection<String> seatCodes) {
        if (seatCodes == null || seatCodes.isEmpty()) {
            return List.of();
        }
        List<String> normalized = seatCodes.stream()
                .map(code -> code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isBlank())
                .toList();
        return seatRepository.findAllByHallIdAndSeatCodeInAndIsDeletedFalse(hallId, normalized);
    }

    private void validateDefinition(PutHallLayoutDefinitionRequest request) {
        Set<String> occupied = new HashSet<>();
        for (PutHallLayoutDefinitionRequest.CellInput cell : request.getCells()) {
            if (cell.getRow() > request.getTotalRows() || cell.getCol() > request.getTotalCols()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            String key = cell.getRow() + ":" + cell.getCol();
            if (!occupied.add(key)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            if (cell.getType() == PutHallLayoutDefinitionRequest.CellInputType.SEAT) {
                if (cell.getSeatType() == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
            } else if (cell.getSeatType() != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    private void publishOutboxEvent(UUID hallId, int seatCount, int cellCount) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("HALL_LAYOUT");
        event.setAggregateId(hallId.toString());
        event.setEventType("HALL_LAYOUT_UPDATED");
        event.setEventPayload("{\"hallId\":\"" + hallId + "\",\"seatCount\":" + seatCount + ",\"cellCount\":" + cellCount + "}");
        outboxEventRepository.save(event);
    }
}
