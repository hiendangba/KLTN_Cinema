package com.cinema.booking_service.dto.request;

import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.PageRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRevenueReportRequest {

    @Valid
    private DateRange dateRange;

    @Valid
    @NotNull(message = "Page request is required")
    private PageRequest<BookingRevenueField> pageRequest;
}
