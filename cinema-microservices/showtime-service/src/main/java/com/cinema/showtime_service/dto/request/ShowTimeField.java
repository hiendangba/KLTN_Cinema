package com.cinema.showtime_service.dto.request;

public enum ShowTimeField {
    ID("id"),
    START_DATE_TIME("startDateTime"),
    END_DATE_TIME("endDateTime"),
    HALL_ID("hallId"),
    FILM_ID("filmId"),
    STATUS("status"),
    IS_DELETED("isDeleted"),
    TIME_CREATED("timeCreated"),
    TIME_UPDATED("timeUpdated"),
    IS_BOOKED("isBooked");

    private final String entityField;

    ShowTimeField(String entityField) {
        this.entityField = entityField;
    }

    public String getEntityField() {
        return entityField;
    }
}
