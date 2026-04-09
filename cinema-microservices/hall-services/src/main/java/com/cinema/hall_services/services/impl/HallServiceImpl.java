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
import com.cinema.hall_services.dto.response.HallResponse;
import com.cinema.hall_services.entity.Hall;
import com.cinema.hall_services.mapper.HallMapper;
import com.cinema.hall_services.repository.HallRepository;
import com.cinema.hall_services.repository.HallRepositoryImpl;
import com.cinema.hall_services.services.HallService;
import com.cinema.http.HeaderNames;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HallServiceImpl implements HallService {

    HallRepository hallRepository;
    HallRepositoryImpl hallRepositoryImpl;
    HallMapper hallMapper;
    ObjectMapper objectMapper;

    @Override
    @Transactional
    public HallResponse createHall(HallCreateRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);

        if (hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(request.getCinemaId(), request.getName())) {
            throw new BusinessException(ErrorCode.HALL_NAME_EXISTED);
        }
        validateLayoutJson(request.getLayoutJson());

        Hall hall = hallMapper.toEntity(request);
        hall.setStatus(request.getStatus() == null ? HallEnum.HallStatus.ACTIVE : request.getStatus());
        hall.setLayoutJson(toJsonString(request.getLayoutJson()));
        hall = hallRepository.save(hall);
        return toHallResponse(hall);
    }

    @Override
    @Transactional(readOnly = true)
    public HallResponse getHallById(UUID id) {
        Hall hall = hallRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.HALL_NOT_FOUND));
        return toHallResponse(hall);
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
            List<Hall> prevHalls = hallRepositoryImpl.previousCursor(cursorParts, keyword, size, sortFields, filterFields);
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
    public HallResponse updateHallLayout(UUID hallId, UpdateHallLayoutRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);

        Hall hall = hallRepository.findByIdAndIsDeletedFalse(hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HALL_NOT_FOUND));

        if (hall.getStatus() == HallEnum.HallStatus.MAINTENANCE) {
            throw new BusinessException(ErrorCode.HALL_MAINTENANCE);
        }
        validateLayoutJson(request.getLayoutJson());

        hall.setLayoutJson(toJsonString(request.getLayoutJson()));
        hall = hallRepository.save(hall);
        return toHallResponse(hall);
    }

    private HallResponse toHallResponse(Hall hall) {
        HallResponse response = hallMapper.toResponse(hall);
        response.setLayoutJson(toJsonNode(hall.getLayoutJson()));
        return response;
    }

    private String toJsonString(JsonNode jsonNode) {
        try {
            return objectMapper.writeValueAsString(jsonNode);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateLayoutJson(JsonNode layoutJson) {
        if (layoutJson == null || layoutJson.isNull() || layoutJson.isMissingNode()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        validateSeatTypeNode(layoutJson);
    }

    private void validateSeatTypeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                validateSeatTypeNode(child);
            }
            return;
        }

        if (node.isObject()) {
            JsonNode seatTypeNode = node.has("seatType") ? node.get("seatType") : node.get("type");
            if (seatTypeNode != null && seatTypeNode.isTextual()) {
                try {
                    HallEnum.SeatType.valueOf(seatTypeNode.asText());
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT);
                }
            }
            node.fields().forEachRemaining(field -> validateSeatTypeNode(field.getValue()));
        }
    }

    private JsonNode toJsonNode(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!"MANAGER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
