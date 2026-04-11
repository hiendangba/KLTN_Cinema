package com.cinema.hall_services.dto.request;

import com.cinema.Enum.HallEnum;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.hall_services.entity.Hall;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Getter
@Slf4j
public enum HallField {
    ID("id", UUID.class),
    CINEMA_ID("cinemaId", UUID.class),
    NAME("name", String.class),
    STATUS("status", HallEnum.HallStatus.class),
    IS_DELETED("isDeleted", Boolean.class),
    TIME_CREATED("timeCreated", LocalDateTime.class),
    TIME_UPDATED("timeUpdated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    HallField(String entityField, Class<?> dataType) {
        this.entityField = entityField;
        this.dataType = dataType;
    }

    public static Comparable<?> convertValue(String value, Class<?> dataType) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        String normalizedValue = value.trim();

        try {
            if (dataType == String.class) {
                return normalizedValue;
            } else if (dataType == Integer.class) {
                return Integer.parseInt(normalizedValue);
            } else if (dataType == Boolean.class) {
                if (!"true".equalsIgnoreCase(normalizedValue) && !"false".equalsIgnoreCase(normalizedValue)) {
                    throw new IllegalArgumentException("Boolean value must be true or false");
                }
                return Boolean.parseBoolean(normalizedValue);
            } else if (dataType == UUID.class) {
                return UUID.fromString(normalizedValue);
            } else if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalizedValue);
            } else if (dataType == HallEnum.HallStatus.class) {
                return HallEnum.HallStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }

    public static Object[] getFieldValues(Hall entity, List<SortField<HallField>> sortFields) {
        Object[] values = new Object[sortFields.size()];
        try {
            for (int i = 0; i < sortFields.size(); i++) {
                SortField<HallField> sortField = sortFields.get(i);
                switch (sortField.getField()) {
                    case ID -> values[i] = entity.getId();
                    case CINEMA_ID -> values[i] = entity.getCinemaId();
                    case NAME -> values[i] = entity.getName();
                    case STATUS -> values[i] = entity.getStatus();
                    case IS_DELETED -> values[i] = entity.getIsDeleted();
                    case TIME_CREATED -> values[i] = entity.getTimeCreated();
                    case TIME_UPDATED -> values[i] = entity.getTimeUpdated();
                }
            }
            return values;
        } catch (Exception e) {
            log.error("Error getting hall field values for cursor encoding: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
        }
    }
}
