package cinema.seat_service.service.impl;

import cinema.seat_service.dto.request.HallLayoutDefinitionRequest;
import cinema.seat_service.entity.HallLayoutProfile;
import cinema.seat_service.entity.OutboxEvent;
import cinema.seat_service.entity.Seat;
import cinema.seat_service.enums.ScreenPosition;
import cinema.seat_service.enums.SeatType;
import cinema.seat_service.repository.HallLayoutCellRepository;
import cinema.seat_service.repository.HallLayoutProfileRepository;
import cinema.seat_service.repository.OutboxEventRepository;
import cinema.seat_service.repository.SeatRepository;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatLayoutServiceImplTest {

    @Mock
    private HallLayoutProfileRepository hallLayoutProfileRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private HallLayoutCellRepository hallLayoutCellRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private SeatLayoutServiceImpl service;

    @Test
    void createHallLayoutDefinition_allowsMixedRowWithValidCouplePairs() {
        UUID hallId = UUID.randomUUID();
        HallLayoutDefinitionRequest request = request(
                1,
                7,
                seat(1, 1, SeatType.COUPLE),
                seat(1, 2, SeatType.COUPLE),
                aisle(1, 3),
                seat(1, 4, SeatType.STANDARD),
                seat(1, 5, SeatType.VIP),
                seat(1, 6, SeatType.COUPLE),
                seat(1, 7, SeatType.COUPLE)
        );

        when(hallLayoutProfileRepository.findByHallIdAndIsDeletedFalse(hallId)).thenReturn(Optional.empty());
        when(hallLayoutProfileRepository.findById(hallId)).thenReturn(Optional.empty());
        when(seatRepository.findAllByHallId(hallId)).thenReturn(List.of());
        when(hallLayoutCellRepository.findAllByHallId(hallId)).thenReturn(List.of());

        service.createHallLayoutDefinition(hallId, request);

        ArgumentCaptor<List<Seat>> seatCaptor = argumentCaptor();
        verify(seatRepository).saveAll(seatCaptor.capture());
        assertThat(seatCaptor.getValue())
                .extracting(Seat::getSeatType)
                .containsExactly(
                        SeatType.COUPLE,
                        SeatType.COUPLE,
                        SeatType.STANDARD,
                        SeatType.VIP,
                        SeatType.COUPLE,
                        SeatType.COUPLE
                );
        verify(hallLayoutCellRepository).saveAll(anyList());
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void createHallLayoutDefinition_rejectsSingleCoupleSeat() {
        UUID hallId = UUID.randomUUID();
        HallLayoutDefinitionRequest request = request(
                1,
                3,
                seat(1, 1, SeatType.COUPLE),
                seat(1, 2, SeatType.STANDARD),
                seat(1, 3, SeatType.VIP)
        );

        when(hallLayoutProfileRepository.findByHallIdAndIsDeletedFalse(hallId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createHallLayoutDefinition(hallId, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void replaceHallLayoutDefinition_rejectsCoupleSeparatedByAisle() {
        UUID hallId = UUID.randomUUID();
        HallLayoutDefinitionRequest request = request(
                1,
                4,
                seat(1, 1, SeatType.COUPLE),
                aisle(1, 2),
                seat(1, 3, SeatType.COUPLE),
                seat(1, 4, SeatType.COUPLE)
        );

        when(hallLayoutProfileRepository.findByHallIdAndIsDeletedFalse(hallId)).thenReturn(Optional.of(new HallLayoutProfile()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replaceHallLayoutDefinition(hallId, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private HallLayoutDefinitionRequest request(int totalRows, int totalCols,
                                                HallLayoutDefinitionRequest.CellInput... cells) {
        HallLayoutDefinitionRequest request = new HallLayoutDefinitionRequest();
        request.setTotalRows(totalRows);
        request.setTotalCols(totalCols);
        request.setScreenPosition(ScreenPosition.TOP);
        request.setCells(List.of(cells));
        return request;
    }

    private HallLayoutDefinitionRequest.CellInput seat(int row, int col, SeatType seatType) {
        HallLayoutDefinitionRequest.CellInput cell = new HallLayoutDefinitionRequest.CellInput();
        cell.setRow(row);
        cell.setCol(col);
        cell.setType(HallLayoutDefinitionRequest.CellInputType.SEAT);
        cell.setSeatType(seatType);
        return cell;
    }

    private HallLayoutDefinitionRequest.CellInput aisle(int row, int col) {
        HallLayoutDefinitionRequest.CellInput cell = new HallLayoutDefinitionRequest.CellInput();
        cell.setRow(row);
        cell.setCol(col);
        cell.setType(HallLayoutDefinitionRequest.CellInputType.AISLE);
        return cell;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<List<Seat>> argumentCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
