package com.cinema.booking_service.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateStaffBookingRequest extends CreateBookingRequest {
    private UUID customerId;
}
