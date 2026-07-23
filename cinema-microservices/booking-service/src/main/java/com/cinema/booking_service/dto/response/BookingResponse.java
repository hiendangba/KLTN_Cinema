package com.cinema.booking_service.dto.response;

import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BookingResponse {
    private UUID id;
    private UUID showtimeId;
    private UUID cinemaId;
    @Setter
    private String cinemaName;
    @Setter
    private String hallName;
    private String filmTitle;
    private LocalDateTime showtimeStartDateTime;
    private LocalDateTime showtimeEndDateTime;
    private UUID userId;
    private CustomerInfoResponse customerInfo;
    private PaymentStatus paymentStatus;
    private BookingStatus bookingStatus;
    private BigDecimal ticketSubtotal;
    private BigDecimal productSubtotal;
    private BigDecimal finalAmount;
    private UUID promotionId;
    private String promotionCode;
    private String promotionName;
    private BigDecimal promotionDiscountAmount;
    @Setter
    private Long loyaltyPointsUsed;
    @Setter
    private Long loyaltyPointsEarned;
    private BigDecimal payableAmount;
    private LocalDateTime reservedUntil;
    private List<BookingSeatItemResponse> seatItems;
    private List<BookingProductItemResponse> productItems;
    private LocalDateTime timeCreated;
    private LocalDateTime timeUpdated;
}
