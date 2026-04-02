package com.cinema.showtime_service.dto.request;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.showtime_service.entity.ShowTime;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public enum ShowTimeField {
    ID("id", UUID.class),
    START_DATE_TIME("startDateTime", LocalDateTime.class),
    END_DATE_TIME("endDateTime", LocalDateTime.class),
    HALL_ID("hallId", UUID.class),
    FILM_ID("filmId", UUID.class),
    STATUS("status", Integer.class),
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
        if (dataType == String.class) {
            return value;
        } else if (dataType == Integer.class) {
            return Integer.parseInt(value);
        } else if (dataType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (dataType == UUID.class) {
            return UUID.fromString(value);
        } else if (dataType == LocalDateTime.class) {
            return LocalDateTime.parse(value);
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
