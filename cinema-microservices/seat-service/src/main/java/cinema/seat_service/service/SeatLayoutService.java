package cinema.seat_service.service;

import cinema.seat_service.dto.request.HallLayoutDefinitionRequest;
import cinema.seat_service.dto.response.HallLayoutResponse;
import com.cinema.dto.response.ActionMessageResponse;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatLayoutService {
    ActionMessageResponse createHallLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request);

    ActionMessageResponse replaceHallLayoutDefinition(UUID hallId, HallLayoutDefinitionRequest request);

    HallLayoutResponse getHallLayoutDefinition(UUID hallId);

    List<cinema.seat_service.entity.Seat> getSeatsByCodes(UUID hallId, Collection<String> seatCodes);
}
