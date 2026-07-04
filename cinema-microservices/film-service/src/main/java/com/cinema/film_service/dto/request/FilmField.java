package com.cinema.film_service.dto.request;

import com.cinema.Enum.FilmEnum;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.entity.Actor;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.entity.FilmType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Getter
@Slf4j
public enum FilmField {
    ID("id", UUID.class),
    TITLE("title", String.class),
    DIRECTOR("director", String.class),
    ACTOR("actor", String.class),
    TYPE("type", String.class),
    RELEASE_DATE("releaseDate", LocalDate.class),
    DESCRIPTION("description", String.class),
    DURATION("duration", Integer.class),
    POSTER("poster", String.class),
    TRAILER("trailer", String.class),
    COUNTRY("country", String.class),
    LANGUAGE("language", String.class),
    AGE_RATING("ageRating", FilmEnum.AgeRating.class),
    STATUS("status", FilmEnum.FilmStatus.class),
    IS_DELETED("isDeleted", Boolean.class),
    TIME_CREATED("timeCreated", LocalDateTime.class),
    TIME_UPDATED("timeUpdated", LocalDateTime.class);

    private final String entityField;
    private final Class<?> dataType;

    FilmField(String entityField, Class<?> dataType) {
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
            } else if (dataType == LocalDate.class) {
                return LocalDate.parse(normalizedValue);
            } else if (dataType == LocalDateTime.class) {
                return LocalDateTime.parse(normalizedValue);
            } else if (dataType == FilmEnum.AgeRating.class) {
                return FilmEnum.AgeRating.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            } else if (dataType == FilmEnum.FilmStatus.class) {
                return FilmEnum.FilmStatus.valueOf(normalizedValue.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }

        throw new BusinessException(ErrorCode.UN_SUPPORTED_FIELD_TYPE);
    }

    public static Object[] getFieldValues(Film entity, List<SortField<FilmField>> sortFields) {
        Object[] values = new Object[sortFields.size()];
        try {
            for (int i = 0; i < sortFields.size(); i++) {
                SortField<FilmField> sortField = sortFields.get(i);
                switch (sortField.getField()) {
                    case ID:
                        values[i] = entity.getId();
                        break;
                    case TITLE:
                        values[i] = entity.getTitle();
                        break;
                    case DIRECTOR:
                        values[i] = entity.getDirector();
                        break;
                    case ACTOR:
                        values[i] = firstActorName(entity.getActors());
                        break;
                    case TYPE:
                        values[i] = firstTypeName(entity.getTypes());
                        break;
                    case RELEASE_DATE:
                        values[i] = entity.getReleaseDate();
                        break;
                    case DESCRIPTION:
                        values[i] = entity.getDescription();
                        break;
                    case DURATION:
                        values[i] = entity.getDuration();
                        break;
                    case POSTER:
                        values[i] = entity.getPoster();
                        break;
                    case TRAILER:
                        values[i] = entity.getTrailer();
                        break;
                    case COUNTRY:
                        values[i] = entity.getCountry();
                        break;
                    case LANGUAGE:
                        values[i] = entity.getLanguage();
                        break;
                    case AGE_RATING:
                        values[i] = entity.getAgeRating();
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

    private static String firstTypeName(Set<FilmType> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        return types.stream()
                .filter(type -> type != null
                        && !Boolean.TRUE.equals(type.getIsDeleted())
                        && StringUtils.hasText(type.getName()))
                .map(type -> type.getName().trim())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .findFirst()
                .orElse(null);
    }

    private static String firstActorName(Set<Actor> actors) {
        if (actors == null || actors.isEmpty()) {
            return null;
        }
        return actors.stream()
                .filter(actor -> actor != null
                        && !Boolean.TRUE.equals(actor.getIsDeleted())
                        && StringUtils.hasText(actor.getName()))
                .map(actor -> actor.getName().trim())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .findFirst()
                .orElse(null);
    }
}
