package cinema.seat_service.controller;

import cinema.seat_service.dto.request.PutHallLayoutDefinitionRequest;
import cinema.seat_service.dto.response.HallLayoutDefinitionResponse;
import cinema.seat_service.service.SeatLayoutService;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/seats/halls", "/api/halls"})
public class SeatLayoutController extends BaseController {

    private final SeatLayoutService seatLayoutService;

    @PutMapping("/{hallId}/layout-definition")
    public ResponseEntity<APIResponse<ActionMessageResponse>> putLayoutDefinition(
            @PathVariable UUID hallId,
            @Valid @RequestBody PutHallLayoutDefinitionRequest request) {
        return ok(seatLayoutService.putHallLayoutDefinition(hallId, request));
    }

    @GetMapping("/{hallId}/layout-definition")
    public ResponseEntity<APIResponse<HallLayoutDefinitionResponse>> getLayoutDefinition(@PathVariable UUID hallId) {
        return ok(seatLayoutService.getHallLayoutDefinition(hallId));
    }

    @GetMapping("/{hallId}")
    public ResponseEntity<APIResponse<HallLayoutDefinitionResponse>> getLayoutDefinitionAlias(@PathVariable UUID hallId) {
        return ok(seatLayoutService.getHallLayoutDefinition(hallId));
    }
}
