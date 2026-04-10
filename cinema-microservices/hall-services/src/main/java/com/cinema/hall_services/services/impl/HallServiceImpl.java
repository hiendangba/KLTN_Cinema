package com.cinema.hall_services.services.impl;

import com.cinema.Enum.HallEnum;
import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.hall_services.dto.request.HallCreateRequest;
import com.cinema.hall_services.dto.request.HallField;
import com.cinema.hall_services.dto.request.UpdateHallLayoutRequest;
import com.cinema.hall_services.dto.request.UpdateHallRequest;
import com.cinema.hall_services.dto.request.UpdateHallStatusRequest;
import com.cinema.hall_services.dto.response.CinemaResponse;
import com.cinema.hall_services.dto.response.HallResponse;
import com.cinema.hall_services.entity.Hall;
import com.cinema.hall_services.grpc.CinemaGrpcClient;
import com.cinema.hall_services.mapper.HallMapper;
import com.cinema.hall_services.repository.HallRepository;
import com.cinema.hall_services.repository.HallRepositoryImpl;
import com.cinema.hall_services.services.HallService;
import com.cinema.http.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HallServiceImpl implements HallService {

    private static final Set<String> REQUIRED_LAYOUT_ITEM_FIELDS = Set.of(
            "id", "type", "seatType", "row", "col", "rowspan", "colspan", "seatCount");

    HallRepository hallRepository;
    HallRepositoryImpl hallRepositoryImpl;
    HallMapper hallMapper;
    CinemaGrpcClient cinemaGrpcClient;
    ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    @Transactional
    public HallResponse createHall(HallCreateRequest request, HttpServletRequest httpRequest) {
        // validateManagerRole(httpRequest);
        // UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        UUID cinemaId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(cinemaId, request.getName())) {
            throw new BusinessException(ErrorCode.HALL_NAME_EXISTED);
        }

        validateLayoutJson(request.getLayoutJson());

        Hall hall = hallMapper.toEntity(request);
        hall.setCinemaId(cinemaId);
        hall.setStatus(request.getStatus() == null ? HallEnum.HallStatus.ACTIVE : request.getStatus());
        hall.setLayoutJson(toJsonString(request.getLayoutJson()));
        hall = hallRepository.save(hall);
        return toHallResponse(hall);
    }

    @Override
    @Transactional(readOnly = true)
    public HallResponse getHallById(UUID id) {
        return toHallResponse(getActiveHallOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<HallResponse> searchHalls(CursorPageRequest<HallField> request) {
        String[] cursorParts = request.getParsedCompositeCursor();
        String keyword = request.getNormalizedKeyword();
        int size = request.getSizeOrDefault();

        List<SortField<HallField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        sortFields.add(new SortField<>(HallField.ID, "ASC"));

        List<FilterField<HallField>> filterFields = request.getFilterBy();
        List<Hall> halls = hallRepositoryImpl.searchWithCursorAndSortAndFilter(
                cursorParts, keyword, size, sortFields, filterFields);

        boolean hasNext = halls.size() > size;
        String nextCursor = null;
        if (hasNext) {
            halls = halls.subList(0, size);
            nextCursor = CursorPageRequest.encodeCompositeCursor(
                    HallField.getFieldValues(halls.get(halls.size() - 1), sortFields));
        }

        String prevCursor = null;
        if (cursorParts != null && cursorParts.length > 0) {
            List<Hall> prevHalls = hallRepositoryImpl.previousCursor(cursorParts, keyword, size, sortFields,
                    filterFields);
            if (!prevHalls.isEmpty() && prevHalls.size() == size) {
                prevCursor = CursorPageRequest.encodeCompositeCursor(
                        HallField.getFieldValues(prevHalls.get(size - 1), sortFields));
            }
        }

        List<HallResponse> data = halls.stream().map(this::toHallResponse).toList();

        return CursorPageResponse.<HallResponse>builder()
                .data(data)
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasNext(hasNext)
                .size(data.size())
                .build();
    }

    @Override
    @Transactional
    public HallResponse updateHall(UUID hallId, UpdateHallRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);

        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(
                hall.getCinemaId(), request.getName(), hallId)) {
            throw new BusinessException(ErrorCode.HALL_NAME_EXISTED);
        }

        hallMapper.updateEntityFromRequest(hall, request);
        hall = hallRepository.save(hall);
        return toHallResponse(hall);
    }

    @Override
    @Transactional
    public HallResponse updateHallStatus(UUID hallId, UpdateHallStatusRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);
        hall.setStatus(request.getStatus());
        hall = hallRepository.save(hall);
        return toHallResponse(hall);
    }

    @Override
    @Transactional
    public HallResponse updateHallLayout(UUID hallId, UpdateHallLayoutRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);
        if (hall.getStatus() == HallEnum.HallStatus.MAINTENANCE) {
            throw new BusinessException(ErrorCode.HALL_MAINTENANCE);
        }

        validateLayoutJson(request.getLayoutJson());
        hall.setLayoutJson(toJsonString(request.getLayoutJson()));
        hall = hallRepository.save(hall);
        return toHallResponse(hall);
    }

    @Override
    @Transactional
    public void deleteHall(UUID hallId, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Hall hall = getManagedHallOrThrow(hallId, cinemaId);
        hall.setIsDeleted(true);
        hallRepository.save(hall);
    }

    private Hall getActiveHallOrThrow(UUID hallId) {
        return hallRepository.findByIdAndIsDeletedFalse(hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HALL_NOT_FOUND));
    }

    private Hall getManagedHallOrThrow(UUID hallId, UUID cinemaId) {
        Hall hall = getActiveHallOrThrow(hallId);
        if (!cinemaId.equals(hall.getCinemaId())) {
            throw new BusinessException(ErrorCode.HALL_NOT_IN_CINEMA);
        }
        return hall;
    }

    private HallResponse toHallResponse(Hall hall) {
        HallResponse response = hallMapper.toResponse(hall);
        response.setLayoutJson(toJsonNode(hall.getLayoutJson()));
        response.setCinemaResponse(CinemaResponse.builder()
                .id(hall.getCinemaId())
                .build());
        return response;
    }

    private String toJsonString(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private JsonNode toJsonNode(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void validateLayoutJson(JsonNode layoutJson) {
        if (layoutJson == null || layoutJson.isNull() || !layoutJson.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (layoutJson.size() != 1 || !layoutJson.has("items")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        JsonNode itemsNode = layoutJson.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Set<String> itemIds = new HashSet<>();
        Set<String> occupiedCells = new HashSet<>();

        for (int i = 0; i < itemsNode.size(); i++) {
            JsonNode itemNode = itemsNode.get(i);
            validateLayoutItem(itemNode, i, itemIds, occupiedCells);
        }
    }

    private void validateLayoutItem(JsonNode itemNode, int itemIndex, Set<String> itemIds, Set<String> occupiedCells) {
        if (itemNode == null || !itemNode.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        for (String requiredField : REQUIRED_LAYOUT_ITEM_FIELDS) {
            if (!itemNode.has(requiredField)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }

        String id = readRequiredText(itemNode, "id");
        if (!itemIds.add(id)) {
            log.warn("Duplicated layout item id detected: {}", id);
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String type = readRequiredText(itemNode, "type").toUpperCase(Locale.ROOT);
        if (!("SEAT".equals(type) || "AISLE".equals(type))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        int row = readPositiveInt(itemNode, "row");
        int col = readPositiveInt(itemNode, "col");
        int rowspan = readPositiveInt(itemNode, "rowspan");
        int colspan = readPositiveInt(itemNode, "colspan");
        int seatCount = readNonNegativeInt(itemNode);

        JsonNode seatTypeNode = itemNode.get("seatType");
        if ("AISLE".equals(type)) {
            if (seatTypeNode != null && !seatTypeNode.isNull()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (seatCount != 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        } else {
            HallEnum.SeatType seatType = parseSeatType(seatTypeNode);
            if (seatType == HallEnum.SeatType.AISLE) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (seatCount < 1) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            if (seatType == HallEnum.SeatType.COUPLE && (rowspan != 1 || colspan != 2 || seatCount != 2)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }

        int endRow = row + rowspan - 1;
        int endCol = col + colspan - 1;

        for (int r = row; r <= endRow; r++) {
            for (int c = col; c <= endCol; c++) {
                String cellKey = r + ":" + c;
                if (!occupiedCells.add(cellKey)) {
                    log.warn("Overlapped cell detected at item index {}: {}", itemIndex, cellKey);
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
            }
        }
    }

    private String readRequiredText(JsonNode itemNode, String fieldName) {
        JsonNode valueNode = itemNode.get(fieldName);
        if (!(valueNode instanceof StringNode textNode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String value = textNode.asString();
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value.trim();
    }

    private int readPositiveInt(JsonNode itemNode, String fieldName) {
        JsonNode valueNode = itemNode.get(fieldName);
        if (valueNode == null || !valueNode.isIntegralNumber()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        long value = valueNode.asLong();
        if (value < 1 || value > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return (int) value;
    }

    private int readNonNegativeInt(JsonNode itemNode) {
        JsonNode valueNode = itemNode.get("seatCount");
        if (valueNode == null || !valueNode.isIntegralNumber()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        long value = valueNode.asLong();
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return (int) value;
    }

    private HallEnum.SeatType parseSeatType(JsonNode seatTypeNode) {
        if (!(seatTypeNode instanceof StringNode textNode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String seatTypeText = textNode.asString();
        if (seatTypeText == null || seatTypeText.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        try {
            return HallEnum.SeatType.valueOf(seatTypeText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!HeaderNames.ROLE_MANAGER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        String userIdRaw = httpRequest.getHeader(HeaderNames.X_USER_ID);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return cinemaGrpcClient.getCinemaIdByUserId(UUID.fromString(userIdRaw));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.CINEMA_NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            throw ex;
        }
    }
}
