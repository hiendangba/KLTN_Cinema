package cinema.seat_service.service.impl;

import cinema.seat_service.dto.request.HallLayoutDefinitionRequest;
import cinema.seat_service.dto.response.HallLayoutCellResponse;
import cinema.seat_service.dto.response.HallLayoutResponse;
import cinema.seat_service.dto.response.HallLayoutSeatResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public ActionMessageResponse createHallLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request) {
        if (hallLayoutProfileRepository.findByHallIdAndIsDeletedFalse(hallId).isPresent()) {
            throw new BusinessException(ErrorCode.HALL_LAYOUT_ALREADY_EXISTS);
        }
        return upsertDefinition(hallId, request);
    }

    @Override
    @Transactional
    public ActionMessageResponse replaceHallLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request) {
        if (hallLayoutProfileRepository.findByHallIdAndIsDeletedFalse(hallId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return upsertDefinition(hallId, request);
    }

    private ActionMessageResponse upsertDefinition(UUID hallId, HallLayoutDefinitionRequest request) {
        validateDefinition(request);

        HallLayoutProfile profile = hallLayoutProfileRepository.findById(hallId)
                .orElseGet(HallLayoutProfile::new);
        profile.setHallId(hallId);
        profile.setTotalRows(request.getTotalRows());
        profile.setTotalCols(request.getTotalCols());
        profile.setScreenPosition(request.getScreenPosition());
        profile.setIsDeleted(false);
        hallLayoutProfileRepository.save(profile);

        Map<String, HallLayoutDefinitionRequest.CellInput> requestedSeatByCode = new LinkedHashMap<>();
        Map<String, HallLayoutDefinitionRequest.CellInput> requestedCellByCoord = new LinkedHashMap<>();
        for (HallLayoutDefinitionRequest.CellInput input : request.getCells()) {
            if (input.getType() == HallLayoutDefinitionRequest.CellInputType.SEAT) {
                requestedSeatByCode.put(generateSeatCode(input.getRow(), input.getCol()), input);
                continue;
            }
            requestedCellByCoord.put(toCoordKey(input.getRow(), input.getCol()), input);
        }

        List<Seat> existingSeats = seatRepository.findAllByHallId(hallId);
        Map<String, Seat> existingSeatByCode = new LinkedHashMap<>();
        for (Seat existingSeat : existingSeats) {
            existingSeatByCode.put(normalizeSeatCode(existingSeat.getSeatCode()), existingSeat);
        }
        List<Seat> seatsToSave = new ArrayList<>();
        for (Map.Entry<String, HallLayoutDefinitionRequest.CellInput> entry : requestedSeatByCode.entrySet()) {
            String seatCode = entry.getKey();
            HallLayoutDefinitionRequest.CellInput input = entry.getValue();
            Seat seat = existingSeatByCode.getOrDefault(seatCode, new Seat());
            seat.setHallId(hallId);
            seat.setSeatCode(seatCode);
            seat.setRow(input.getRow());
            seat.setCol(input.getCol());
            seat.setSeatType(input.getSeatType());
            seat.setIsDeleted(false);
            seatsToSave.add(seat);
        }
        Set<String> requestedSeatCodes = requestedSeatByCode.keySet();
        for (Seat existingSeat : existingSeats) {
            String seatCode = normalizeSeatCode(existingSeat.getSeatCode());
            if (!requestedSeatCodes.contains(seatCode) && !Boolean.TRUE.equals(existingSeat.getIsDeleted())) {
                existingSeat.setIsDeleted(true);
                seatsToSave.add(existingSeat);
            }
        }
        seatRepository.saveAll(seatsToSave);

        List<HallLayoutCell> existingCells = hallLayoutCellRepository.findAllByHallId(hallId);
        Map<String, HallLayoutCell> existingCellByCoord = new LinkedHashMap<>();
        for (HallLayoutCell existingCell : existingCells) {
            existingCellByCoord.put(toCoordKey(existingCell.getRow(), existingCell.getCol()), existingCell);
        }
        List<HallLayoutCell> cellsToSave = new ArrayList<>();
        for (Map.Entry<String, HallLayoutDefinitionRequest.CellInput> entry : requestedCellByCoord.entrySet()) {
            HallLayoutDefinitionRequest.CellInput input = entry.getValue();
            HallLayoutCell cell = existingCellByCoord.getOrDefault(entry.getKey(), new HallLayoutCell());
            cell.setHallId(hallId);
            cell.setRow(input.getRow());
            cell.setCol(input.getCol());
            cell.setCellType(input.getType() == HallLayoutDefinitionRequest.CellInputType.AISLE
                    ? LayoutCellType.AISLE : LayoutCellType.BLOCKED);
            cell.setIsDeleted(false);
            cellsToSave.add(cell);
        }
        Set<String> requestedCellCoords = requestedCellByCoord.keySet();
        for (HallLayoutCell existingCell : existingCells) {
            String cellCoord = toCoordKey(existingCell.getRow(), existingCell.getCol());
            if (!requestedCellCoords.contains(cellCoord) && !Boolean.TRUE.equals(existingCell.getIsDeleted())) {
                existingCell.setIsDeleted(true);
                cellsToSave.add(existingCell);
            }
        }
        hallLayoutCellRepository.saveAll(cellsToSave);
        publishOutboxEvent(hallId, requestedSeatByCode.size(), requestedCellByCoord.size());

        return ActionMessageResponse.builder()
                .message("Hall layout definition updated successfully")
                .build();
    }

    private String generateSeatCode(Integer row, Integer col) {
        return SeatCodeGenerator.fromRowCol(row, col).toUpperCase(Locale.ROOT);
    }

    private String normalizeSeatCode(String seatCode) {
        return seatCode == null ? "" : seatCode.trim().toUpperCase(Locale.ROOT);
    }

    private String toCoordKey(Integer row, Integer col) {
        return row + ":" + col;
    }

    @Override
    @Transactional(readOnly = true)
    public HallLayoutResponse getHallLayoutDefinition(UUID hallId) {
        HallLayoutProfile profile = hallLayoutProfileRepository.findByHallIdAndIsDeletedFalse(hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        List<HallLayoutSeatResponse> seats = seatRepository.findAllByHallIdAndIsDeletedFalseOrderByRowAscColAsc(hallId)
                .stream()
                .map(seat -> HallLayoutSeatResponse.builder()
                        .id(seat.getId())
                        .seatCode(seat.getSeatCode())
                        .row(seat.getRow())
                        .col(seat.getCol())
                        .seatType(seat.getSeatType())
                        .build())
                .toList();

        List<HallLayoutCellResponse> cells = hallLayoutCellRepository.findAllByHallIdAndIsDeletedFalseOrderByRowAscColAsc(hallId)
                .stream()
                .map(cell -> HallLayoutCellResponse.builder()
                        .id(cell.getId())
                        .row(cell.getRow())
                        .col(cell.getCol())
                        .cellType(cell.getCellType())
                        .build())
                .toList();

        return HallLayoutResponse.builder()
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

    private void validateDefinition(HallLayoutDefinitionRequest request) {
        if (request.getTotalRows() == null || request.getTotalRows() <= 0
                || request.getTotalCols() == null || request.getTotalCols() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (request.getCells() == null || request.getCells().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Set<String> occupied = new HashSet<>();
        for (HallLayoutDefinitionRequest.CellInput cell : request.getCells()) {
            if (cell.getRow() == null || cell.getRow() <= 0
                    || cell.getCol() == null || cell.getCol() <= 0
                    || cell.getRow() > request.getTotalRows()
                    || cell.getCol() > request.getTotalCols()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            String key = cell.getRow() + ":" + cell.getCol();
            if (!occupied.add(key)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }

            if (cell.getType() == HallLayoutDefinitionRequest.CellInputType.SEAT) {
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
