package com.cinema.cinema_service.dto.request;

import com.cinema.cinema_service.entity.Cinema;
import com.cinema.cinema_service.enums.CinemaStatus;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Getter
@Slf4j
public enum CinemaField {
    ID("id", UUID.class),
    CODE("code", String.class),
    NAME("name", String.class),
    ADDRESS("address", String.class),
    LATITUDE("latitude", BigDecimal.class),
    LONGITUDE("longitude", BigDecimal.class),
    PHONE("phone", String.class),
    OPEN_TIME("openTime", LocalTime.class),
    CLOSE_TIME("closeTime", LocalTime.class),
    STATUS("status", CinemaStatus.class),
    MANAGER_ID("managerId", UUID.class),
    IS_DELETED("isDeleted", Boolean.class),
    CREATED_AT("createdAt", LocalDateTime.class),
    UPDATED_AT("updatedAt", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    CinemaField(String entityField, Class<?> dataType) {
        this.entityField = entityField;
        this.dataType = dataType;
    }

    public static Comparable<?> convertValue(String value, Class<?> dataType) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
        String normalized = value.trim();
        try {
            if (dataType == String.class) {
                return normalized;
            } else if (dataType == UUID.class) {
                return UUID.fromString(normalized);
            } else if (dataType == Boolean.class) {
                if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
                    throw new IllegalArgumentException("Boolean value must be true or false");
                }
                return Boolean.parseBoolean(normalized);
            } else if (dataType == BigDecimal.class) {
                return new BigDecimal(normalized);
            } else if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalized);
            } else if (dataType == LocalTime.class) {
                return LocalTime.parse(normalized);
            } else if (dataType == CinemaStatus.class) {
                return CinemaStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }

    public static Object[] getFieldValues(Cinema entity, List<SortField<CinemaField>> sortFields) {
        Object[] values = new Object[sortFields.size()];
        try {
            for (int i = 0; i < sortFields.size(); i++) {
                SortField<CinemaField> sortField = sortFields.get(i);
                switch (sortField.getField()) {
                    case ID -> values[i] = entity.getId();
                    case CODE -> values[i] = entity.getCode();
                    case NAME -> values[i] = entity.getName();
                    case ADDRESS -> values[i] = entity.getAddress();
                    case LATITUDE -> values[i] = entity.getLatitude();
                    case LONGITUDE -> values[i] = entity.getLongitude();
                    case PHONE -> values[i] = entity.getPhone();
                    case OPEN_TIME -> values[i] = entity.getOpenTime();
                    case CLOSE_TIME -> values[i] = entity.getCloseTime();
                    case STATUS -> values[i] = entity.getStatus();
                    case MANAGER_ID -> values[i] = entity.getManagerId();
                    case IS_DELETED -> values[i] = entity.getIsDeleted();
                    case CREATED_AT -> values[i] = entity.getCreatedAt();
                    case UPDATED_AT -> values[i] = entity.getUpdatedAt();
                }
            }
            return values;
        } catch (Exception ex) {
            log.error("Error getting cinema field values for cursor encoding: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
        }
    }
}
