package cinema.seat_service.dto.response;

import cinema.seat_service.enums.LayoutCellType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class HallLayoutCellResponse {
    private UUID id;
    private Integer row;
    private Integer col;
    private LayoutCellType cellType;
}
