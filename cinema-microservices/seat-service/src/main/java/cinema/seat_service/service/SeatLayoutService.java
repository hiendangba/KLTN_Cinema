package cinema.seat_service.service;

import cinema.seat_service.dto.request.PutHallLayoutDefinitionRequest;
import cinema.seat_service.dto.response.HallLayoutDefinitionResponse;
import com.cinema.dto.response.ActionMessageResponse;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatLayoutService {
    ActionMessageResponse createHallLayoutDefinition(UUID hallId, PutHallLayoutDefinitionRequest request);

    ActionMessageResponse replaceHallLayoutDefinition(UUID hallId, PutHallLayoutDefinitionRequest request);

    HallLayoutDefinitionResponse getHallLayoutDefinition(UUID hallId);

    List<cinema.seat_service.entity.Seat> getSeatsByCodes(UUID hallId, Collection<String> seatCodes);
}
