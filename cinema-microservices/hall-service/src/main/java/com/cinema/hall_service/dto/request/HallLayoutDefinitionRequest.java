package com.cinema.hall_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class HallLayoutDefinitionRequest {
    @NotNull(message = "totalRows is required")
    @Positive(message = "totalRows must be greater than 0")
    private Integer totalRows;

    @NotNull(message = "totalCols is required")
    @Positive(message = "totalCols must be greater than 0")
    private Integer totalCols;

    @NotNull(message = "screenPosition is required")
    private ScreenPosition screenPosition;

    @Valid
    @NotEmpty(message = "cells must not be empty")
    private List<CellInput> cells = new ArrayList<>();

    public enum ScreenPosition {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    public enum CellInputType {
        SEAT, // Ghế ngồi
        AISLE, // Lối đi
        BLOCKED // Khu vực không sử dụng
    }

    public enum SeatType {
        STANDARD, // Ghế tiêu chuẩn
        VIP, // Ghế VIP
        COUPLE // Ghế đôi
    }

    @Getter
    @Setter
    public static class CellInput {
        @NotNull(message = "row is required")
        @Positive(message = "row must be greater than 0")
        private Integer row;

        @NotNull(message = "col is required")
        @Positive(message = "col must be greater than 0")
        private Integer col;

        @NotNull(message = "type is required")
        private CellInputType type;

        private SeatType seatType;
    }
}
