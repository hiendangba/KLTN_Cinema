package com.cinema.booking_service.dto.request;

import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.PageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowtimePerformanceReportRequest {

    @Valid
    private DateRange dateRange;

    @Builder.Default
    private List<UUID> cinemaIds = new ArrayList<>();

    @Builder.Default
    private List<UUID> filmIds = new ArrayList<>();

    @Builder.Default
    private List<UUID> selectedIds = new ArrayList<>();

    @Valid
    @NotNull(message = "Page request is required")
    private PageRequest<ShowtimePerformanceField> pageRequest;
}
