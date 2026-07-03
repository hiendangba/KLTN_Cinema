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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void suggestSeatSuggestions_shouldFallbackForOddSeatCountWhenPreferCoupleEnabled() {
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
                        .seatCount(5)
                        .preferCoupleSeat(true)
                        .build());

        assertEquals(showtimeId, response.getShowtimeId());
        assertEquals(5, response.getRequestedSeatCount());
        assertTrue(response.getPreferCoupleSeat());
        assertTrue(response.getCandidates().size() > 0);
        assertTrue(response.getCandidates().get(0).getHasCoupleSeats());
    }

    @Test
    void suggestSeatSuggestions_shouldPreferOneCouplePairAndOneNearestSingleForThreeSeats() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                7,
                "TOP",
                seat("A1", 1, 1, "STANDARD"),
                seat("A2", 1, 2, "STANDARD"),
                seat("A3", 1, 3, "COUPLE"),
                seat("A4", 1, 4, "COUPLE"),
                seat("A5", 1, 5, "STANDARD"),
                seat("A6", 1, 6, "STANDARD"),
                seat("A7", 1, 7, "STANDARD")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of());

        SeatSuggestionResponse response = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(3)
                        .preferCoupleSeat(true)
                        .build());

        assertTrue(response.getCandidates().get(0).getHasCoupleSeats());
        assertTrue(response.getCandidates().get(0).getSeatCodes().containsAll(List.of("A3", "A4")));
        assertEquals(3, response.getCandidates().get(0).getSeatCount());
        assertTrue(
                response.getCandidates().get(0).getSeatCodes().contains("A2")
                        || response.getCandidates().get(0).getSeatCodes().contains("A5"));
    }

    @Test
    void suggestSeatSuggestions_shouldNotSplitCouplePairsForFourSeats() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                6,
                "TOP",
                seat("A1", 1, 1, "COUPLE"),
                seat("A2", 1, 2, "COUPLE"),
                seat("A3", 1, 3, "COUPLE"),
                seat("A4", 1, 4, "COUPLE"),
                seat("A5", 1, 5, "COUPLE"),
                seat("A6", 1, 6, "COUPLE")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of());

        SeatSuggestionResponse response = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(4)
                        .preferCoupleSeat(true)
                        .build());

        assertEquals(List.of("A3", "A4", "A5", "A6"), response.getCandidates().get(0).getSeatCodes());
        assertFalse(response.getCandidates().stream().anyMatch(candidate -> candidate.getSeatCodes().equals(List.of("A2", "A3", "A4", "A5"))));
    }

    @Test
    void suggestSeatSuggestions_shouldUseMaximumAvailableCouplePairsThenStandardSeats() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                6,
                "TOP",
                seat("A1", 1, 1, "COUPLE"),
                seat("A2", 1, 2, "COUPLE"),
                seat("A3", 1, 3, "STANDARD"),
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
                        .seatCount(4)
                        .preferCoupleSeat(true)
                        .build());

        assertTrue(response.getCandidates().get(0).getSeatCodes().containsAll(List.of("A1", "A2")));
        assertTrue(response.getCandidates().get(0).getSeatCodes().containsAll(List.of("A3", "A4")));
    }

    @Test
    void suggestSeatSuggestions_shouldPreferNearestUpperRightFallbackWhenCoupleEnabled() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                10,
                14,
                "TOP",
                seat("H7", 8, 7, "STANDARD"),
                seat("I7", 9, 7, "STANDARD"),
                seat("I8", 9, 8, "STANDARD"),
                seat("I9", 9, 9, "STANDARD"),
                seat("I10", 9, 10, "STANDARD"),
                seat("J7", 10, 7, "COUPLE"),
                seat("J8", 10, 8, "COUPLE"),
                seat("J9", 10, 9, "COUPLE"),
                seat("J10", 10, 10, "COUPLE"),
                seat("J11", 10, 11, "STANDARD"),
                seat("J12", 10, 12, "STANDARD"),
                seat("J13", 10, 13, "STANDARD"),
                seat("J14", 10, 14, "STANDARD")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of());

        SeatSuggestionResponse response = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(5)
                        .preferCoupleSeat(true)
                        .build());

        assertEquals(List.of("I9", "J7", "J8", "J9", "J10"), response.getCandidates().get(0).getSeatCodes());
        assertFalse(response.getCandidates().get(0).getSeatCodes().contains("H7"));
        assertFalse(response.getCandidates().get(0).getSeatCodes().contains("I7"));
        assertFalse(response.getCandidates().get(0).getSeatCodes().contains("J11"));
    }

    @Test
    void suggestSeatSuggestions_shouldPreferRightmostCoupleBlockWhenTwoBlocksExist() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                2,
                14,
                "TOP",
                seat("I13", 1, 13, "STANDARD"),
                seat("I14", 1, 14, "STANDARD"),
                seat("J1", 2, 1, "COUPLE"),
                seat("J2", 2, 2, "COUPLE"),
                seat("J13", 2, 13, "COUPLE"),
                seat("J14", 2, 14, "COUPLE")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of());

        SeatSuggestionResponse response = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(5)
                        .preferCoupleSeat(true)
                        .build());

        // Same distance on the fallback row means the rightmost seat wins.
        assertEquals(List.of("I14", "J1", "J2", "J13", "J14"), response.getCandidates().get(0).getSeatCodes());
        assertFalse(response.getCandidates().get(0).getSeatCodes().contains("I13"));
    }

    @Test
    void suggestSeatSuggestions_shouldNotCrossFixedCouplePairBoundaries() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                10,
                "TOP",
                seat("J5", 1, 5, "COUPLE"),
                seat("J6", 1, 6, "COUPLE"),
                seat("J7", 1, 7, "COUPLE"),
                seat("J8", 1, 8, "COUPLE"),
                seat("J9", 1, 9, "COUPLE"),
                seat("J10", 1, 10, "COUPLE")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of());

        SeatSuggestionResponse responseForThreeSeats = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(3)
                        .preferCoupleSeat(true)
                        .build());

        assertFalse(responseForThreeSeats.getCandidates().stream()
                .anyMatch(candidate -> candidate.getSeatCodes().equals(List.of("J5", "J6", "J7"))));

        SeatSuggestionResponse responseForFourSeats = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(4)
                        .preferCoupleSeat(true)
                        .build());

        assertFalse(responseForFourSeats.getCandidates().stream()
                .anyMatch(candidate -> candidate.getSeatCodes().equals(List.of("J6", "J7", "J8", "J9"))));
        assertTrue(responseForFourSeats.getCandidates().stream()
                .anyMatch(candidate -> candidate.getSeatCodes().equals(List.of("J5", "J6", "J7", "J8"))
                        || candidate.getSeatCodes().equals(List.of("J7", "J8", "J9", "J10"))));
    }

    @Test
    void suggestSeatSuggestions_shouldDropWholeCouplePairWhenOneSeatIsUnavailable() {
        UUID showtimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();

        ShowTime showTime = showTime(showtimeId, hallId, policyId);
        PricingPolicy pricingPolicy = pricingPolicy(policyId);
        SeatGrpcClient.LayoutBundle layoutBundle = layoutBundle(
                1,
                10,
                "TOP",
                seat("J5", 1, 5, "COUPLE"),
                seat("J6", 1, 6, "COUPLE"),
                seat("J7", 1, 7, "COUPLE"),
                seat("J8", 1, 8, "COUPLE"),
                seat("J9", 1, 9, "COUPLE"),
                seat("J10", 1, 10, "COUPLE")
        );

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(policyId)).thenReturn(Optional.of(pricingPolicy));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), anyList())).thenReturn(Map.of(
                "J8", "BOOKED"
        ));

        SeatSuggestionResponse response = service.suggestSeatSuggestions(
                showtimeId,
                SeatSuggestionRequest.builder()
                        .seatCount(2)
                        .preferCoupleSeat(true)
                        .build());

        assertFalse(response.getCandidates().stream()
                .flatMap(candidate -> candidate.getSeatCodes().stream())
                .anyMatch(seatCode -> seatCode.equals("J7") || seatCode.equals("J8")));
        assertTrue(response.getCandidates().stream()
                .anyMatch(candidate -> candidate.getSeatCodes().equals(List.of("J5", "J6"))
                        || candidate.getSeatCodes().equals(List.of("J9", "J10"))));
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
