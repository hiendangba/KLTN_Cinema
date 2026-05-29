package com.cinema.showtime_service.services.impl;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.response.FilmResponse;
import com.cinema.showtime_service.dto.response.HallResponse;
import com.cinema.showtime_service.dto.response.PricingPolicyResponse;
import com.cinema.showtime_service.dto.response.ShowTimeResponse;
import com.cinema.showtime_service.entity.PricingPolicy;
import com.cinema.showtime_service.entity.ShowTime;
import com.cinema.showtime_service.grpc.BookingGrpcClient;
import com.cinema.showtime_service.grpc.CinemaGrpcClient;
import com.cinema.showtime_service.grpc.FilmGrpcClient;
import com.cinema.showtime_service.grpc.HallGrpcClient;
import com.cinema.showtime_service.grpc.SeatGrpcClient;
import com.cinema.showtime_service.mapper.PricingPolicyMapper;
import com.cinema.showtime_service.mapper.ShowTimeMapper;
import com.cinema.showtime_service.repository.PricingPolicyRepository;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import com.cinema.showtime_service.repository.ShowTimeRepositoryImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowTimeServiceImplTest {

    @Mock
    private ShowTimeRepository showTimeRepository;
    @Mock
    private ShowTimeRepositoryImpl showTimeRepositoryImpl;
    @Mock
    private ShowTimeMapper showTimeMapper;
    @Mock
    private PricingPolicyMapper pricingPolicyMapper;
    @Mock
    private FilmGrpcClient filmGrpcClient;
    @Mock
    private BookingGrpcClient bookingGrpcClient;
    @Mock
    private CinemaGrpcClient cinemaGrpcClient;
    @Mock
    private HallGrpcClient hallGrpcClient;
    @Mock
    private SeatGrpcClient seatGrpcClient;
    @Mock
    private PricingPolicyRepository pricingPolicyRepository;

    @InjectMocks
    private ShowTimeServiceImpl showTimeService;

    @Test
    void createShowTime_shouldCreateWhenHallAndPricingBelongToCinema() {
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = managerRequest(userId);
        ShowTimeCreateRequest createRequest = ShowTimeCreateRequest.builder()
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDateTime.now().plusDays(1))
                .endDateTime(LocalDateTime.now().plusDays(1).plusHours(4))
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build();

        PricingPolicy pricingPolicy = new PricingPolicy();
        pricingPolicy.setId(pricingPolicyId);
        pricingPolicy.setCinemaId(cinemaId);

        PricingPolicyResponse pricingPolicyResponse = PricingPolicyResponse.builder()
                .id(pricingPolicyId)
                .cinemaId(cinemaId)
                .standardPrice(50000L)
                .vipPrice(70000L)
                .couplePrice(120000L)
                .build();

        FilmResponse filmResponse = FilmResponse.builder()
                .id(filmId)
                .duration(90)
                .build();

        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER)).thenReturn(List.of(cinemaId));
        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(cinemaId);
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)).thenReturn(Optional.of(pricingPolicy));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(filmResponse);
        when(showTimeRepository.findOverlapping(any(), any(), any())).thenReturn(Optional.empty());
        when(showTimeMapper.toEntity(any(ShowTimeCreateRequest.class))).thenAnswer(invocation -> {
            ShowTimeCreateRequest req = invocation.getArgument(0);
            ShowTime st = new ShowTime();
            st.setId(UUID.randomUUID());
            st.setHallId(req.getHallId());
            st.setFilmId(req.getFilmId());
            st.setPricingPolicyId(req.getPricingPolicyId());
            st.setStartDateTime(req.getStartDateTime());
            st.setEndDateTime(req.getEndDateTime());
            st.setStatus(req.getStatus());
            st.setIsDeleted(false);
            st.setTimeCreated(LocalDateTime.now());
            st.setTimeUpdated(LocalDateTime.now());
            return st;
        });
        when(showTimeRepository.save(any(ShowTime.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pricingPolicyRepository.findAllById(any())).thenReturn(List.of(pricingPolicy));
        when(pricingPolicyMapper.toResponse(pricingPolicy)).thenReturn(pricingPolicyResponse);
        when(showTimeMapper.toResponse(any(ShowTime.class))).thenAnswer(invocation -> {
            ShowTime st = invocation.getArgument(0);
            return ShowTimeResponse.builder()
                    .id(st.getId())
                    .hallId(st.getHallId())
                    .filmId(st.getFilmId())
                    .pricingPolicyId(st.getPricingPolicyId())
                    .startDateTime(st.getStartDateTime())
                    .endDateTime(st.getEndDateTime())
                    .status(st.getStatus())
                    .isDeleted(Boolean.TRUE.equals(st.getIsDeleted()))
                    .timeCreated(st.getTimeCreated())
                    .timeUpdated(st.getTimeUpdated())
                    .build();
        });

        var result = showTimeService.createShowTime(createRequest, request);

        assertNotNull(result);
        assertNotNull(result.getSuccessResponse());
        assertEquals(1, result.getSuccessResponse().size());
        assertEquals(pricingPolicyId, result.getSuccessResponse().get(0).getData().getPricingPolicyId());
    }

    @Test
    void createShowTime_shouldThrowWhenHallNotInCinema() {
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID otherCinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = managerRequest(userId);
        ShowTimeCreateRequest createRequest = ShowTimeCreateRequest.builder()
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDateTime.now().plusDays(1))
                .endDateTime(LocalDateTime.now().plusDays(1).plusHours(3))
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build();

        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER)).thenReturn(List.of(cinemaId));
        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(otherCinemaId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> showTimeService.createShowTime(createRequest, request));

        assertEquals(ErrorCode.HALL_NOT_IN_CINEMA, ex.getErrorCode());
    }

    @Test
    void updateShowTime_shouldThrowWhenPricingPolicyNotInCinema() {
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID otherCinemaId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = managerRequest(userId);
        UpdateShowTimeRequest updateRequest = UpdateShowTimeRequest.builder()
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDateTime.now().plusDays(1))
                .endDateTime(LocalDateTime.now().plusDays(1).plusHours(3))
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build();

        ShowTime existing = new ShowTime();
        existing.setId(showTimeId);
        existing.setHallId(hallId);
        existing.setFilmId(filmId);
        existing.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        existing.setIsDeleted(false);

        PricingPolicy pricingPolicy = new PricingPolicy();
        pricingPolicy.setId(pricingPolicyId);
        pricingPolicy.setCinemaId(otherCinemaId);

        when(showTimeRepository.findById(showTimeId)).thenReturn(Optional.of(existing));
        when(bookingGrpcClient.isShowtimeBooked(showTimeId)).thenReturn(false);
        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER)).thenReturn(List.of(cinemaId));
        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(cinemaId);
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)).thenReturn(Optional.of(pricingPolicy));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> showTimeService.updateShowTime(showTimeId, updateRequest, request));

        assertEquals(ErrorCode.PRICING_POLICY_NOT_IN_CINEMA, ex.getErrorCode());
    }

    @Test
    void getActiveShowtimesByFilmId_shouldReturnPagedActiveShowtimes() {
        UUID filmId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();

        ShowTime showTime = new ShowTime();
        showTime.setId(showtimeId);
        showTime.setFilmId(filmId);
        showTime.setHallId(hallId);
        showTime.setPricingPolicyId(pricingPolicyId);
        showTime.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        showTime.setIsDeleted(false);
        showTime.setStartDateTime(LocalDateTime.now().plusHours(1));
        showTime.setEndDateTime(LocalDateTime.now().plusHours(3));

        ShowTimeResponse mappedResponse = ShowTimeResponse.builder()
                .id(showtimeId)
                .filmId(filmId)
                .hallId(hallId)
                .pricingPolicyId(pricingPolicyId)
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build();

        PricingPolicy policy = new PricingPolicy();
        policy.setId(pricingPolicyId);
        PricingPolicyResponse policyResponse = PricingPolicyResponse.builder().id(pricingPolicyId).build();

        FilmResponse filmResponse = FilmResponse.builder().id(filmId).build();
        HallResponse hallResponse = HallResponse.builder().id(hallId).build();
        SeatGrpcClient.LayoutBundle layoutBundle = SeatGrpcClient.LayoutBundle.builder()
                .totalRows(2)
                .totalCols(3)
                .screenPosition("TOP")
                .seats(List.of(
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A1")
                                .setRow(1)
                                .setCol(1)
                                .setSeatType("STANDARD")
                                .build(),
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A2")
                                .setRow(1)
                                .setCol(2)
                                .setSeatType("STANDARD")
                                .build(),
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("B1")
                                .setRow(2)
                                .setCol(1)
                                .setSeatType("VIP")
                                .build(),
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("B2")
                                .setRow(2)
                                .setCol(2)
                                .setSeatType("VIP")
                                .build()))
                .cells(List.<com.cinema.grpc.seat.LayoutCellPayload>of())
                .build();

        when(showTimeRepositoryImpl.countWithFilter(eq(null), any())).thenReturn(1L);
        when(showTimeRepositoryImpl.searchWithPageAndSortAndFilter(eq(null), eq(1), eq(20), any(), any()))
                .thenReturn(List.of(showTime));
        when(showTimeMapper.toResponse(showTime)).thenReturn(mappedResponse);
        when(pricingPolicyRepository.findAllById(List.of(pricingPolicyId))).thenReturn(List.of(policy));
        when(pricingPolicyMapper.toResponse(policy)).thenReturn(policyResponse);
        when(filmGrpcClient.getFilmsByIds(List.of(filmId))).thenReturn(java.util.Map.of(filmId, filmResponse));
        when(hallGrpcClient.getHallById(hallId)).thenReturn(hallResponse);
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), eq(List.of("A1", "A2", "B1", "B2"))))
                .thenReturn(Map.of(
                        "A1", "BOOKED",
                        "A2", "LOCKED",
                        "B1", "AVAILABLE",
                        "B2", "AVAILABLE"));

        var result = showTimeService.getActiveShowtimesByFilmId(filmId, null, null);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getData().size());
        assertEquals(filmId, result.getData().get(0).getFilmId());
        assertNotNull(result.getData().get(0).getFilm());
        assertNotNull(result.getData().get(0).getHall());
        assertNotNull(result.getData().get(0).getPricingPolicy());
        assertEquals(4, result.getData().get(0).getTotalSeatCapacity());
        assertEquals(2, result.getData().get(0).getOccupiedSeats());
        assertEquals(2, result.getData().get(0).getAvailableSeats());
    }

    private HttpServletRequest managerRequest(UUID userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("MANAGER");
        when(request.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
        return request;
    }
}
