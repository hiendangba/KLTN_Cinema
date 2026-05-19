package cinema.seat_service.dto.response;

import cinema.seat_service.enums.ScreenPosition;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class HallLayoutDefinitionResponse {
    private UUID hallId;
    private Integer totalRows;
    private Integer totalCols;
    private ScreenPosition screenPosition;
    @Builder.Default
    private List<LayoutSeatResponse> seats = new ArrayList<>();
    @Builder.Default
    private List<LayoutCellResponse> cells = new ArrayList<>();
}
