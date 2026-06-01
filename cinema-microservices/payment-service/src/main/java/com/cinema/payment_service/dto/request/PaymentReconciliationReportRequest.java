package com.cinema.payment_service.dto.request;

import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.PageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReconciliationReportRequest {

    @Valid
    private DateRange dateRange;

    @Valid
    @NotNull(message = "Page request is required")
    private PageRequest<PaymentSessionField> pageRequest;
}
