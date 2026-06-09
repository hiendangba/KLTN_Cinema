package com.cinema.showtime_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.showtime_service.dto.request.SeatSuggestionRequest;
import com.cinema.showtime_service.dto.response.SeatSuggestionResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.grpc.BookingGrpcClient;
import com.cinema.showtime_service.grpc.SeatGrpcClient;
import com.cinema.showtime_service.repository.PricingPolicyRepository;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatSuggestionServiceImplTest {

    @Mock
    private ShowTimeRepository showTimeRepository;
    @Mock
    private PricingPolicyRepository pricingPolicyRepository;
    @Mock
    private SeatGrpcClient seatGrpcClient;
    @Mock
    private BookingGrpcClient bookingGrpcClient;

    @InjectMocks
    private SeatSuggestionServiceImpl service;

    @Test
    void suggestSeatSuggestions_shouldReturnCoupleBlockFirstWhenPreferCoupleEnabled() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                6,
                "TOP",
                seat("A1", 1, 1, "STANDARD"),
                seat("A2", 1, 2, "COUPLE"),
                seat("A3", 1, 3, "COUPLE"),
                seat("A4", 1, 4, "STANDARD"),
                seat("A5", 1, 5, "STANDARD"),
                seat("A6", 1, 6, "STANDARD")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of());

        SeatSuggestionResponse response = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(2)
                        .preferCoupleSeat(true)
                        .build());

        assertEquals(showtimeId, response.getShowtimeId());
        assertEquals(2, response.getRequestedSeatCount());
        assertTrue(response.getPreferCoupleSeat());
        assertEquals(5, response.getCandidates().size());
        assertEquals(List.of("A2", "A3"), response.getCandidates().get(0).getSeatCodes());
        assertTrue(response.getCandidates().get(0).getHasCoupleSeats());
    }

    @Test
    void suggestSeatSuggestions_shouldFallbackWhenNoExactBlockExists() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                5,
                "TOP",
                seat("A1", 1, 1, "STANDARD"),
                seat("A2", 1, 2, "STANDARD"),
                seat("A4", 1, 4, "STANDARD"),
                seat("A5", 1, 5, "STANDARD")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of());

        SeatSuggestionResponse response = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(4)
                        .preferCoupleSeat(false)
                        .build());

        assertEquals(1, response.getCandidates().size());
        assertEquals(List.of("A1", "A2", "A4", "A5"), response.getCandidates().get(0).getSeatCodes());
        assertTrue(response.getCandidates().get(0).getReason().contains("Fallback"));
    }

    @Test
    void suggestSeatSuggestions_shouldRejectWhenAvailableSeatsAreInsufficient() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                3,
                "TOP",
                seat("A1", 1, 1, "STANDARD"),
                seat("A2", 1, 2, "STANDARD"),
                seat("A3", 1, 3, "STANDARD")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of(
                "A1", "BOOKED"
        ));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.suggestSeatSuggestions(
                        showtimeId,
                        SeatSuggestionRequest.builder()
                                .seatCount(4)
                                .preferCoupleSeat(false)
                                .build()));

        assertEquals(ErrorCode.INSUFFICIENT_AVAILABLE_SEATS, exception.getErrorCode());
    }

    @Test
    void suggestSeatSuggestions_shouldRejectOddSeatCountWhenPreferCoupleEnabled() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.suggestSeatSuggestions(
                        UUID.randomUUID(),
                        SeatSuggestionRequest.builder()
                                .seatCount(3)
                                .preferCoupleSeat(true)
                                .build()));

        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    @Test
    void suggestSeatSuggestions_shouldRejectSeatCountGreaterThanFive() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.suggestSeatSuggestions(
                        UUID.randomUUID(),
                        SeatSuggestionRequest.builder()
                                .seatCount(6)
                                .preferCoupleSeat(false)
                                .build()));

        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
    }

    private ShowTime showTime(UUID showtimeId, UUID hallId, UUID pricingPolicyId) {
        ShowTime showTime = new ShowTime();
        showTime.setId(showtimeId);
        showTime.setHallId(hallId);
        showTime.setPricingPolicyId(pricingPolicyId);
        showTime.setIsDeleted(false);
        return showTime;
    }

    private PricingPolicy pricingPolicy(UUID policyId) {
        PricingPolicy policy = new PricingPolicy();
        policy.setId(policyId);
        policy.setStandardPrice(50000L);
        policy.setVipPrice(70000L);
        policy.setCouplePrice(120000L);
        policy.setIsDeleted(false);
        return policy;
    }

    private SeatGrpcClient.LayoutBundle layoutBundle(int rows, int cols, String screenPosition,
                                                     com.cinema.grpc.seat.LayoutSeatPayload... seats) {
        return SeatGrpcClient.LayoutBundle.builder()
                .totalRows(rows)
                .totalCols(cols)
                .screenPosition(screenPosition)
                .seats(List.of(seats))
                .cells(List.of())
                .build();
    }

    private com.cinema.grpc.seat.LayoutSeatPayload seat(String seatCode, int row, int col, String seatType) {
        return com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                .setSeatCode(seatCode)
                .setRow(row)
                .setCol(col)
                .setSeatType(seatType)
                .build();
    }
}
