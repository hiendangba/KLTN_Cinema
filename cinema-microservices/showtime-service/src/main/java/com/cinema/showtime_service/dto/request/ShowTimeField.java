package com.cinema.showtime_service.dto.request;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.showtime_service.entity.ShowTime;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Getter
@Slf4j
public enum ShowTimeField {
    ID("id", UUID.class),
    START_DATE_TIME("startDateTime", LocalDateTime.class),
    END_DATE_TIME("endDateTime", LocalDateTime.class),
    HALL_ID("hallId", UUID.class),
    FILM_ID("filmId", UUID.class),
    PRICING_POLICY_ID("pricingPolicyId", UUID.class),
    STATUS("status", ShowTimeEnum.ShowTimeStatus.class),
    IS_DELETED("isDeleted", Boolean.class),
    TIME_CREATED("timeCreated", LocalDateTime.class),
    TIME_UPDATED("timeUpdated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    ShowTimeField(String entityField, Class<?> dataType) {
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
            } else if (dataType == ShowTimeEnum.ShowTimeStatus.class) {
                return ShowTimeEnum.ShowTimeStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }

    public static Object[] getFieldValues(ShowTime entity, List<SortField<ShowTimeField>> sortFields) {
        Object[] values = new Object[sortFields.size()];
        try {
            for (int i = 0; i < sortFields.size(); i++) {
                SortField<ShowTimeField> sortField = sortFields.get(i);
                switch (sortField.getField()) {
                    case ID:
                        values[i] = entity.getId();
                        break;
                    case START_DATE_TIME:
                        values[i] = entity.getStartDateTime();
                        break;
                    case END_DATE_TIME:
                        values[i] = entity.getEndDateTime();
                        break;
                    case HALL_ID:
                        values[i] = entity.getHallId();
                        break;
                    case FILM_ID:
                        values[i] = entity.getFilmId();
                        break;
                    case PRICING_POLICY_ID:
                        values[i] = entity.getPricingPolicyId();
                        break;
                    case STATUS:
                        values[i] = entity.getStatus();
                        break;
                    case IS_DELETED:
                        values[i] = entity.getIsDeleted();
                        break;
                    case TIME_CREATED:
                        values[i] = entity.getTimeCreated();
                        break;
                    case TIME_UPDATED:
                        values[i] = entity.getTimeUpdated();
                        break;
                }
            }
            return values;
        } catch (Exception e) {
            log.error("Error getting field values for cursor encoding: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
        }
    }
}
