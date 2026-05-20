package cinema.seat_service.dto.response;

import cinema.seat_service.enums.SeatType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class HallLayoutSeatResponse {
    private UUID id;
    private String seatCode;
    private Integer row;
    private Integer col;
    private SeatType seatType;
}
