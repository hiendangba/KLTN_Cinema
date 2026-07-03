package com.cinema.showtime_service.services.impl;

import com.cinema.Enum.ShowTimeEnum;
import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.showtime_service.dto.request.SearchShowtimesByFilmRequest;
import com.cinema.showtime_service.dto.request.ShowTimeCreateRequest;
import com.cinema.showtime_service.dto.request.ShowTimeField;
import com.cinema.showtime_service.dto.request.UpdateShowTimeRequest;
import com.cinema.showtime_service.dto.response.CinemaOperatingHoursResponse;
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
import org.mockito.ArgumentCaptor;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("MANAGER");
        when(request.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
        ShowTimeCreateRequest createRequest = ShowTimeCreateRequest.builder()
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDate.now().plusDays(1).atTime(9, 0))
                .endDateTime(LocalDate.now().plusDays(1).atTime(12, 0))
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
        when(cinemaGrpcClient.getCinemaById(cinemaId)).thenReturn(cinemaOperatingHours(cinemaId, "08:00", "23:00"));
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

        var result = showTimeService.createShowTime(createRequest, request);

        assertNotNull(result);
        assertEquals(SuccessMessage.SHOWTIME_CREATED.getMessage(), result.getMessage());

        ArgumentCaptor<ShowTime> showTimeCaptor = ArgumentCaptor.forClass(ShowTime.class);
        verify(showTimeRepository).save(showTimeCaptor.capture());
        assertEquals(pricingPolicyId, showTimeCaptor.getValue().getPricingPolicyId());
        assertEquals(hallId, showTimeCaptor.getValue().getHallId());
        assertEquals(filmId, showTimeCaptor.getValue().getFilmId());
    }

    @Test
    void createShowTime_shouldRejectPastStartDateTime() {
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("MANAGER");
        ShowTimeCreateRequest createRequest = ShowTimeCreateRequest.builder()
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDateTime.now().minusHours(1))
                .endDateTime(LocalDateTime.now().plusHours(2))
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> showTimeService.createShowTime(createRequest, request));

        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verifyNoInteractions(cinemaGrpcClient);
        verifyNoInteractions(filmGrpcClient);
        verifyNoInteractions(hallGrpcClient);
    }

    @Test
    void createShowTime_shouldCreateSlotsAcrossCloseTimeWhenStartIsValid() {
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
                .startDateTime(LocalDate.now().plusDays(1).atTime(22, 0))
                .endDateTime(LocalDate.now().plusDays(2).atTime(10, 30))
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build();

        PricingPolicy pricingPolicy = new PricingPolicy();
        pricingPolicy.setId(pricingPolicyId);
        pricingPolicy.setCinemaId(cinemaId);

        FilmResponse filmResponse = FilmResponse.builder()
                .id(filmId)
                .duration(90)
                .build();

        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER)).thenReturn(List.of(cinemaId));
        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(cinemaId);
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)).thenReturn(Optional.of(pricingPolicy));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(filmResponse);
        when(cinemaGrpcClient.getCinemaById(cinemaId)).thenReturn(cinemaOperatingHours(cinemaId, "08:00", "23:00"));
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

        var result = showTimeService.createShowTime(createRequest, request);

        assertNotNull(result);
        assertEquals(SuccessMessage.SHOWTIME_CREATED.getMessage(), result.getMessage());

        ArgumentCaptor<ShowTime> showTimeCaptor = ArgumentCaptor.forClass(ShowTime.class);
        verify(showTimeRepository, times(2)).save(showTimeCaptor.capture());
        List<ShowTime> createdShowTimes = showTimeCaptor.getAllValues();
        assertEquals(2, createdShowTimes.size());
        assertEquals(LocalDate.now().plusDays(1).atTime(22, 0), createdShowTimes.get(0).getStartDateTime());
        assertEquals(LocalDate.now().plusDays(2).atTime(0, 0), createdShowTimes.get(0).getEndDateTime());
        assertEquals(LocalDate.now().plusDays(2).atTime(8, 0), createdShowTimes.get(1).getStartDateTime());
        assertEquals(LocalDate.now().plusDays(2).atTime(10, 0), createdShowTimes.get(1).getEndDateTime());
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
    void createShowTime_shouldAllowAdminWithoutCinemaAssignment() {
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = adminRequest(UUID.randomUUID());
        ShowTimeCreateRequest createRequest = ShowTimeCreateRequest.builder()
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDate.now().plusDays(1).atTime(9, 0))
                .endDateTime(LocalDate.now().plusDays(1).atTime(12, 0))
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build();

        PricingPolicy pricingPolicy = new PricingPolicy();
        pricingPolicy.setId(pricingPolicyId);
        pricingPolicy.setCinemaId(cinemaId);

        PricingPolicyResponse pricingPolicyResponse = PricingPolicyResponse.builder()
                .id(pricingPolicyId)
                .cinemaId(cinemaId)
                .build();

        FilmResponse filmResponse = FilmResponse.builder()
                .id(filmId)
                .duration(90)
                .build();

        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(cinemaId);
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)).thenReturn(Optional.of(pricingPolicy));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(filmResponse);
        when(cinemaGrpcClient.getCinemaById(cinemaId)).thenReturn(cinemaOperatingHours(cinemaId, "08:00", "23:00"));
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

        var result = showTimeService.createShowTime(createRequest, request);

        assertNotNull(result);
        assertEquals(SuccessMessage.SHOWTIME_CREATED.getMessage(), result.getMessage());
        verify(showTimeRepository).save(any(ShowTime.class));
        verify(cinemaGrpcClient, times(0)).getCinemaIdsByUserId(any(), any());
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
    void updateShowTime_shouldAllowAdminWithoutCinemaAssignment() {
        UUID cinemaId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = adminRequest(UUID.randomUUID());
        UpdateShowTimeRequest updateRequest = UpdateShowTimeRequest.builder()
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(0))
                .endDateTime(LocalDateTime.now().plusDays(1).withHour(12).withMinute(30))
                .status(ShowTimeEnum.ShowTimeStatus.ONGOING)
                .build();

        ShowTime existing = new ShowTime();
        existing.setId(showTimeId);
        existing.setHallId(hallId);
        existing.setFilmId(filmId);
        existing.setPricingPolicyId(pricingPolicyId);
        existing.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        existing.setIsDeleted(false);

        PricingPolicy pricingPolicy = new PricingPolicy();
        pricingPolicy.setId(pricingPolicyId);
        pricingPolicy.setCinemaId(cinemaId);

        when(showTimeRepository.findById(showTimeId)).thenReturn(Optional.of(existing));
        when(bookingGrpcClient.isShowtimeBooked(showTimeId)).thenReturn(false);
        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(cinemaId);
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)).thenReturn(Optional.of(pricingPolicy));
        when(cinemaGrpcClient.getCinemaById(cinemaId)).thenReturn(cinemaOperatingHours(cinemaId, "08:00", "23:00"));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(FilmResponse.builder().id(filmId).duration(90).build());
        when(showTimeRepository.findOverlappingExcludingId(showTimeId, hallId,
                updateRequest.getStartDateTime(), updateRequest.getEndDateTime())).thenReturn(Optional.empty());
        when(showTimeRepository.save(existing)).thenReturn(existing);

        var result = showTimeService.updateShowTime(showTimeId, updateRequest, request);

        assertNotNull(result);
        assertEquals(ShowTimeEnum.ShowTimeStatus.ONGOING, existing.getStatus());
        verify(cinemaGrpcClient, times(0)).getCinemaIdsByUserId(any(), any());
    }

    @Test
    void deleteShowTime_shouldAllowAdminWithoutCinemaAssignment() {
        UUID showTimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();

        HttpServletRequest request = adminRequest(UUID.randomUUID());

        ShowTime existing = new ShowTime();
        existing.setId(showTimeId);
        existing.setHallId(hallId);
        existing.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        existing.setIsDeleted(false);

        when(showTimeRepository.findById(showTimeId)).thenReturn(Optional.of(existing));
        when(bookingGrpcClient.isShowtimeBooked(showTimeId)).thenReturn(false);

        var result = showTimeService.deleteShowTime(showTimeId, request);

        assertNotNull(result);
        assertTrue(existing.getIsDeleted());
        verify(showTimeRepository).save(existing);
        verify(cinemaGrpcClient, times(0)).getCinemaIdsByUserId(any(), any());
    }

    @Test
    void promoteScheduledShowtimesToOngoing_shouldUseLookbackWindowAndUpdateRepository() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 3, 10, 15);

        when(showTimeRepository.promoteScheduledToOngoing(now.minusDays(7), now)).thenReturn(3);

        int updated = showTimeService.promoteScheduledShowtimesToOngoing(now, 7);

        assertEquals(3, updated);
        verify(showTimeRepository).promoteScheduledToOngoing(now.minusDays(7), now);
    }

    @Test
    void expireOngoingShowtimes_shouldDelegateToRepository() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 3, 10, 20);

        when(showTimeRepository.expireOngoingShowtimes(now)).thenReturn(5);

        int updated = showTimeService.expireOngoingShowtimes(now);

        assertEquals(5, updated);
        verify(showTimeRepository).expireOngoingShowtimes(now);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShowtimes_shouldAllowFinishedStatusFilter() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("ADMIN");

        PageRequest<ShowTimeField> pageRequest = PageRequest.<ShowTimeField>builder()
                .page(1)
                .size(10)
                .filterBy(List.of(FilterField.<ShowTimeField>builder()
                        .field(ShowTimeField.STATUS)
                        .operator("EQ")
                        .value(ShowTimeEnum.ShowTimeStatus.FINISHED)
                        .build()))
                .build();

        when(showTimeRepositoryImpl.countWithFilter(eq(null), any())).thenReturn(0L);
        when(showTimeRepositoryImpl.searchWithPageAndSortAndFilter(eq(null), eq(1), eq(10), any(), any()))
                .thenReturn(List.of());

        var result = showTimeService.searchShowtimes(pageRequest, request);

        assertNotNull(result);
        assertEquals(0L, result.getTotalElements());

        ArgumentCaptor<List> filterCaptor = ArgumentCaptor.forClass(List.class);
        verify(showTimeRepositoryImpl).countWithFilter(eq(null), filterCaptor.capture());

        List<FilterField<ShowTimeField>> filters = (List<FilterField<ShowTimeField>>) filterCaptor.getValue();
        assertEquals(1L, filters.stream().filter(filter -> filter.getField() == ShowTimeField.STATUS).count());
        assertTrue(filters.stream().anyMatch(filter ->
                filter.getField() == ShowTimeField.STATUS
                        && "EQ".equalsIgnoreCase(filter.getOperator())
                        && filter.getValue() == ShowTimeEnum.ShowTimeStatus.FINISHED));
    }

    @Test
    void searchShowtimes_shouldSearchByFilmTitleKeyword() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("ADMIN");

        UUID filmId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();

        PageRequest<ShowTimeField> pageRequest = PageRequest.<ShowTimeField>builder()
                .page(1)
                .size(10)
                .keyword("Cuối")
                .build();

        ShowTime showTime = new ShowTime();
        showTime.setId(showtimeId);
        showTime.setHallId(hallId);
        showTime.setFilmId(filmId);
        showTime.setPricingPolicyId(pricingPolicyId);
        showTime.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        showTime.setIsDeleted(false);

        PricingPolicy policy = new PricingPolicy();
        policy.setId(pricingPolicyId);
        PricingPolicyResponse policyResponse = PricingPolicyResponse.builder().id(pricingPolicyId).build();
        FilmResponse filmResponse = FilmResponse.builder()
                .id(filmId)
                .title("VENOM: KÈO CUỐI")
                .build();
        HallResponse hallResponse = HallResponse.builder()
                .id(hallId)
                .cinemaId(cinemaId)
                .name("Phòng Chiếu CS1")
                .build();
        SeatGrpcClient.LayoutBundle layoutBundle = SeatGrpcClient.LayoutBundle.builder()
                .totalRows(1)
                .totalCols(1)
                .screenPosition("TOP")
                .seats(List.of(
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A1")
                                .setRow(1)
                                .setCol(1)
                                .setSeatType("STANDARD")
                                .build()))
                .cells(List.of())
                .build();

        when(showTimeRepositoryImpl.searchAllWithSortAndFilter(any(), any())).thenReturn(List.of(showTime));
        when(showTimeMapper.toResponse(showTime)).thenReturn(ShowTimeResponse.builder()
                .id(showtimeId)
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build());
        when(pricingPolicyRepository.findAllById(List.of(pricingPolicyId))).thenReturn(List.of(policy));
        when(pricingPolicyMapper.toResponse(policy)).thenReturn(policyResponse);
        when(filmGrpcClient.getFilmsByIds(List.of(filmId))).thenReturn(Map.of(filmId, filmResponse));
        when(hallGrpcClient.getHallById(hallId)).thenReturn(hallResponse);
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenReturn("CinemaStar Lê Văn Việt");
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), eq(List.of("A1"))))
                .thenReturn(Map.of("A1", "AVAILABLE"));

        var result = showTimeService.searchShowtimes(pageRequest, request);

        assertNotNull(result);
        assertEquals(1L, result.getTotalElements());
        assertEquals(1, result.getData().size());
        assertEquals("VENOM: KÈO CUỐI", result.getData().get(0).getFilm().getTitle());
        assertEquals("Phòng Chiếu CS1", result.getData().get(0).getHall().getName());
        verify(showTimeRepositoryImpl).searchAllWithSortAndFilter(any(), any());
    }

    @Test
    void searchShowtimes_shouldSearchByHallNameKeyword() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("ADMIN");

        UUID filmId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();

        PageRequest<ShowTimeField> pageRequest = PageRequest.<ShowTimeField>builder()
                .page(1)
                .size(10)
                .keyword("Phòng Chiếu CS1")
                .build();

        ShowTime showTime = new ShowTime();
        showTime.setId(showtimeId);
        showTime.setHallId(hallId);
        showTime.setFilmId(filmId);
        showTime.setPricingPolicyId(pricingPolicyId);
        showTime.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        showTime.setIsDeleted(false);

        PricingPolicy policy = new PricingPolicy();
        policy.setId(pricingPolicyId);
        PricingPolicyResponse policyResponse = PricingPolicyResponse.builder().id(pricingPolicyId).build();
        FilmResponse filmResponse = FilmResponse.builder()
                .id(filmId)
                .title("Movie A")
                .build();
        HallResponse hallResponse = HallResponse.builder()
                .id(hallId)
                .cinemaId(cinemaId)
                .name("Phòng Chiếu CS1")
                .build();
        SeatGrpcClient.LayoutBundle layoutBundle = SeatGrpcClient.LayoutBundle.builder()
                .totalRows(1)
                .totalCols(1)
                .screenPosition("TOP")
                .seats(List.of(
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A1")
                                .setRow(1)
                                .setCol(1)
                                .setSeatType("STANDARD")
                                .build()))
                .cells(List.of())
                .build();

        when(showTimeRepositoryImpl.searchAllWithSortAndFilter(any(), any())).thenReturn(List.of(showTime));
        when(showTimeMapper.toResponse(showTime)).thenReturn(ShowTimeResponse.builder()
                .id(showtimeId)
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .status(ShowTimeEnum.ShowTimeStatus.SCHEDULED)
                .build());
        when(pricingPolicyRepository.findAllById(List.of(pricingPolicyId))).thenReturn(List.of(policy));
        when(pricingPolicyMapper.toResponse(policy)).thenReturn(policyResponse);
        when(filmGrpcClient.getFilmsByIds(List.of(filmId))).thenReturn(Map.of(filmId, filmResponse));
        when(hallGrpcClient.getHallById(hallId)).thenReturn(hallResponse);
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenReturn("CinemaStar Lê Văn Việt");
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), eq(List.of("A1"))))
                .thenReturn(Map.of("A1", "AVAILABLE"));

        var result = showTimeService.searchShowtimes(pageRequest, request);

        assertNotNull(result);
        assertEquals(1L, result.getTotalElements());
        assertEquals(1, result.getData().size());
        assertEquals("Phòng Chiếu CS1", result.getData().get(0).getHall().getName());
        verify(showTimeRepositoryImpl).searchAllWithSortAndFilter(any(), any());
    }

    @Test
    void updateShowTime_shouldUpdateStatusInSameRequest() {
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = managerRequest(userId);
        UpdateShowTimeRequest updateRequest = UpdateShowTimeRequest.builder()
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDate.now().plusDays(1).atTime(9, 0))
                .endDateTime(LocalDate.now().plusDays(1).atTime(12, 0))
                .status(ShowTimeEnum.ShowTimeStatus.ONGOING)
                .build();

        ShowTime existing = new ShowTime();
        existing.setId(showTimeId);
        existing.setHallId(hallId);
        existing.setFilmId(filmId);
        existing.setPricingPolicyId(UUID.randomUUID());
        existing.setStartDateTime(LocalDateTime.now().plusDays(2));
        existing.setEndDateTime(LocalDateTime.now().plusDays(2).plusHours(3));
        existing.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        existing.setIsDeleted(false);

        PricingPolicy pricingPolicy = new PricingPolicy();
        pricingPolicy.setId(pricingPolicyId);
        pricingPolicy.setCinemaId(cinemaId);

        FilmResponse filmResponse = FilmResponse.builder()
                .id(filmId)
                .duration(90)
                .build();

        when(showTimeRepository.findById(showTimeId)).thenReturn(Optional.of(existing));
        when(bookingGrpcClient.isShowtimeBooked(showTimeId)).thenReturn(false);
        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER)).thenReturn(List.of(cinemaId));
        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(cinemaId);
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)).thenReturn(Optional.of(pricingPolicy));
        when(cinemaGrpcClient.getCinemaById(cinemaId)).thenReturn(cinemaOperatingHours(cinemaId, "08:00", "23:00"));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(filmResponse);
        when(showTimeRepository.save(any(ShowTime.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = showTimeService.updateShowTime(showTimeId, updateRequest, request);

        assertNotNull(result);
        ArgumentCaptor<ShowTime> captor = ArgumentCaptor.forClass(ShowTime.class);
        verify(showTimeRepository).save(captor.capture());

        ShowTime saved = captor.getValue();
        assertEquals(pricingPolicyId, saved.getPricingPolicyId());
        assertEquals(updateRequest.getStartDateTime(), saved.getStartDateTime());
        assertEquals(updateRequest.getEndDateTime(), saved.getEndDateTime());
        assertEquals(ShowTimeEnum.ShowTimeStatus.ONGOING, saved.getStatus());
    }

    @Test
    void updateShowTime_shouldRejectRequestOutsideOperatingHours() {
        UUID userId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID filmId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();

        HttpServletRequest request = managerRequest(userId);
        UpdateShowTimeRequest updateRequest = UpdateShowTimeRequest.builder()
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(LocalDate.now().plusDays(1).atTime(23, 30))
                .endDateTime(LocalDate.now().plusDays(2).atTime(2, 0))
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
        pricingPolicy.setCinemaId(cinemaId);

        FilmResponse filmResponse = FilmResponse.builder()
                .id(filmId)
                .duration(90)
                .build();

        when(showTimeRepository.findById(showTimeId)).thenReturn(Optional.of(existing));
        when(bookingGrpcClient.isShowtimeBooked(showTimeId)).thenReturn(false);
        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER)).thenReturn(List.of(cinemaId));
        when(hallGrpcClient.getCinemaIdByHallId(hallId)).thenReturn(cinemaId);
        when(pricingPolicyRepository.findByIdAndIsDeletedFalse(pricingPolicyId)).thenReturn(Optional.of(pricingPolicy));
        when(cinemaGrpcClient.getCinemaById(cinemaId)).thenReturn(cinemaOperatingHours(cinemaId, "08:00", "23:00"));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(filmResponse);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> showTimeService.updateShowTime(showTimeId, updateRequest, request));

        assertEquals(ErrorCode.INVALID_END_TIME, ex.getErrorCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShowtimesByFilmId_shouldFilterByDateAndCinemaAndSort() {
        UUID filmId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        UUID pricingPolicyId = UUID.randomUUID();
        UUID showtimeId = UUID.randomUUID();

        SearchShowtimesByFilmRequest request = SearchShowtimesByFilmRequest.builder()
                .page(2)
                .size(10)
                .date(LocalDate.of(2026, 5, 30))
                .cinemaId(cinemaId)
                .build();

        ShowTime showTime = new ShowTime();
        showTime.setId(showtimeId);
        showTime.setFilmId(filmId);
        showTime.setHallId(hallId);
        showTime.setPricingPolicyId(pricingPolicyId);
        showTime.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        showTime.setIsDeleted(false);
        showTime.setStartDateTime(LocalDateTime.of(2026, 5, 30, 9, 0));
        showTime.setEndDateTime(LocalDateTime.of(2026, 5, 30, 11, 0));

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
        HallResponse hallResponse = HallResponse.builder().id(hallId).cinemaId(cinemaId).build();
        SeatGrpcClient.LayoutBundle layoutBundle = SeatGrpcClient.LayoutBundle.builder()
                .totalRows(1)
                .totalCols(1)
                .screenPosition("TOP")
                .seats(List.of(
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A1")
                                .setRow(1)
                                .setCol(1)
                                .setSeatType("STANDARD")
                                .build()))
                .cells(List.of())
                .build();

        when(hallGrpcClient.listActiveHallIdsByCinema(cinemaId)).thenReturn(List.of(hallId));
        when(showTimeRepositoryImpl.countWithFilter(eq(null), any())).thenReturn(1L);
        when(showTimeRepositoryImpl.searchWithPageAndSortAndFilter(eq(null), eq(2), eq(10), any(), any()))
                .thenReturn(List.of(showTime));
        when(showTimeMapper.toResponse(showTime)).thenReturn(mappedResponse);
        when(pricingPolicyRepository.findAllById(List.of(pricingPolicyId))).thenReturn(List.of(policy));
        when(pricingPolicyMapper.toResponse(policy)).thenReturn(policyResponse);
        when(filmGrpcClient.getFilmsByIds(List.of(filmId))).thenReturn(Map.of(filmId, filmResponse));
        when(hallGrpcClient.getHallById(hallId)).thenReturn(hallResponse);
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenReturn("Cinema Alpha");
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), eq(List.of("A1"))))
                .thenReturn(Map.of("A1", "AVAILABLE"));

        var result = showTimeService.searchShowtimesByFilmId(filmId, request);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getData().size());
        assertEquals(filmId, result.getData().get(0).getFilmId());
        assertNotNull(result.getData().get(0).getHall());
        assertEquals("Cinema Alpha", result.getData().get(0).getHall().getCinemaName());

        ArgumentCaptor<List> filterCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> sortCaptor = ArgumentCaptor.forClass(List.class);
        verify(showTimeRepositoryImpl).countWithFilter(eq(null), filterCaptor.capture());
        verify(showTimeRepositoryImpl).searchWithPageAndSortAndFilter(eq(null), eq(2), eq(10), sortCaptor.capture(), any());

        List<com.cinema.dto.request.FilterField<ShowTimeField>> filters =
                (List<com.cinema.dto.request.FilterField<ShowTimeField>>) filterCaptor.getValue();
        assertTrue(filters.stream().anyMatch(filter -> filter.getField() == ShowTimeField.FILM_ID));
        assertTrue(filters.stream().anyMatch(filter -> filter.getField() == ShowTimeField.START_DATE_TIME));
        assertTrue(filters.stream().anyMatch(filter ->
                filter.getField() == ShowTimeField.START_DATE_TIME
                        && "GTE".equalsIgnoreCase(filter.getOperator())));
        assertTrue(filters.stream().anyMatch(filter ->
                filter.getField() == ShowTimeField.STATUS
                        && "IN".equalsIgnoreCase(filter.getOperator())));
        assertTrue(filters.stream().anyMatch(filter -> filter.getField() == ShowTimeField.HALL_ID));

        List<com.cinema.dto.request.SortField<ShowTimeField>> sorts =
                (List<com.cinema.dto.request.SortField<ShowTimeField>>) sortCaptor.getValue();
        assertEquals(2, sorts.size());
        assertEquals(ShowTimeField.START_DATE_TIME, sorts.get(0).getField());
        assertEquals("ASC", sorts.get(0).getDirection());
        assertEquals(ShowTimeField.ID, sorts.get(1).getField());
        assertEquals("ASC", sorts.get(1).getDirection());
    }

    @Test
    void getShowTimeById_shouldEnrichCinemaName() {
        UUID filmId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
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
        showTime.setStartDateTime(LocalDateTime.of(2026, 5, 30, 9, 0));
        showTime.setEndDateTime(LocalDateTime.of(2026, 5, 30, 11, 0));

        PricingPolicy policy = new PricingPolicy();
        policy.setId(pricingPolicyId);
        PricingPolicyResponse policyResponse = PricingPolicyResponse.builder().id(pricingPolicyId).build();
        FilmResponse filmResponse = FilmResponse.builder().id(filmId).build();
        HallResponse hallResponse = HallResponse.builder().id(hallId).cinemaId(cinemaId).build();
        SeatGrpcClient.LayoutBundle layoutBundle = SeatGrpcClient.LayoutBundle.builder()
                .totalRows(1)
                .totalCols(1)
                .screenPosition("TOP")
                .seats(List.of(
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A1")
                                .setRow(1)
                                .setCol(1)
                                .setSeatType("STANDARD")
                                .build()))
                .cells(List.of())
                .build();

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(filmResponse);
        when(pricingPolicyRepository.findById(pricingPolicyId)).thenReturn(Optional.of(policy));
        when(pricingPolicyMapper.toResponse(policy)).thenReturn(policyResponse);
        when(hallGrpcClient.getHallById(hallId)).thenReturn(hallResponse);
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenReturn("Cinema Alpha");
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), eq(List.of("A1"))))
                .thenReturn(Map.of("A1", "AVAILABLE"));
        when(showTimeMapper.toResponse(showTime)).thenReturn(ShowTimeResponse.builder()
                .id(showtimeId)
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(showTime.getStartDateTime())
                .endDateTime(showTime.getEndDateTime())
                .status(showTime.getStatus())
                .isDeleted(false)
                .timeCreated(showTime.getTimeCreated())
                .timeUpdated(showTime.getTimeUpdated())
                .build());

        var result = showTimeService.getShowTimeById(showtimeId);

        assertNotNull(result);
        assertNotNull(result.getHall());
        assertEquals("Cinema Alpha", result.getHall().getCinemaName());
    }

    @Test
    void getShowTimeById_shouldFallbackToNullWhenCinemaNameLookupFails() {
        UUID filmId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
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
        showTime.setStartDateTime(LocalDateTime.of(2026, 5, 30, 9, 0));
        showTime.setEndDateTime(LocalDateTime.of(2026, 5, 30, 11, 0));

        PricingPolicy policy = new PricingPolicy();
        policy.setId(pricingPolicyId);
        PricingPolicyResponse policyResponse = PricingPolicyResponse.builder().id(pricingPolicyId).build();
        FilmResponse filmResponse = FilmResponse.builder().id(filmId).build();
        HallResponse hallResponse = HallResponse.builder().id(hallId).cinemaId(cinemaId).build();
        SeatGrpcClient.LayoutBundle layoutBundle = SeatGrpcClient.LayoutBundle.builder()
                .totalRows(1)
                .totalCols(1)
                .screenPosition("TOP")
                .seats(List.of(
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A1")
                                .setRow(1)
                                .setCol(1)
                                .setSeatType("STANDARD")
                                .build()))
                .cells(List.of())
                .build();

        when(showTimeRepository.findById(showtimeId)).thenReturn(Optional.of(showTime));
        when(filmGrpcClient.getFilmById(filmId)).thenReturn(filmResponse);
        when(pricingPolicyRepository.findById(pricingPolicyId)).thenReturn(Optional.of(policy));
        when(pricingPolicyMapper.toResponse(policy)).thenReturn(policyResponse);
        when(hallGrpcClient.getHallById(hallId)).thenReturn(hallResponse);
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenThrow(new BusinessException(ErrorCode.CINEMA_SERVICE_ERROR));
        when(seatGrpcClient.getLayoutByHallId(hallId)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(eq(showtimeId), eq(List.of("A1"))))
                .thenReturn(Map.of("A1", "AVAILABLE"));
        when(showTimeMapper.toResponse(showTime)).thenReturn(ShowTimeResponse.builder()
                .id(showtimeId)
                .hallId(hallId)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId)
                .startDateTime(showTime.getStartDateTime())
                .endDateTime(showTime.getEndDateTime())
                .status(showTime.getStatus())
                .isDeleted(false)
                .timeCreated(showTime.getTimeCreated())
                .timeUpdated(showTime.getTimeUpdated())
                .build());

        var result = showTimeService.getShowTimeById(showtimeId);

        assertNotNull(result);
        assertNotNull(result.getHall());
        assertNull(result.getHall().getCinemaName());
    }

    @Test
    void searchShowtimesByFilmId_shouldReturnEmptyPageWhenCinemaHasNoHall() {
        UUID filmId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();

        SearchShowtimesByFilmRequest request = SearchShowtimesByFilmRequest.builder()
                .page(1)
                .size(20)
                .date(LocalDate.of(2026, 5, 30))
                .cinemaId(cinemaId)
                .build();

        when(hallGrpcClient.listActiveHallIdsByCinema(cinemaId)).thenReturn(List.of());

        var result = showTimeService.searchShowtimesByFilmId(filmId, request);

        assertNotNull(result);
        assertEquals(0L, result.getTotalElements());
        assertEquals(0, result.getData().size());
        verifyNoInteractions(showTimeRepositoryImpl);
    }

    @Test
    void searchShowtimesByFilmRequest_shouldRejectMissingDate() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SearchShowtimesByFilmRequest request = SearchShowtimesByFilmRequest.builder()
                .page(1)
                .size(20)
                .build();

        assertTrue(validator.validate(request).stream()
                .anyMatch(violation -> "date".equals(violation.getPropertyPath().toString())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShowtimesByFilmId_shouldCacheCinemaNameByCinemaId() {
        UUID filmId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId1 = UUID.randomUUID();
        UUID hallId2 = UUID.randomUUID();
        UUID pricingPolicyId1 = UUID.randomUUID();
        UUID pricingPolicyId2 = UUID.randomUUID();
        UUID showtimeId1 = UUID.randomUUID();
        UUID showtimeId2 = UUID.randomUUID();

        SearchShowtimesByFilmRequest request = SearchShowtimesByFilmRequest.builder()
                .page(1)
                .size(20)
                .date(LocalDate.of(2026, 5, 30))
                .cinemaId(cinemaId)
                .build();

        ShowTime showTime1 = new ShowTime();
        showTime1.setId(showtimeId1);
        showTime1.setFilmId(filmId);
        showTime1.setHallId(hallId1);
        showTime1.setPricingPolicyId(pricingPolicyId1);
        showTime1.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        showTime1.setIsDeleted(false);
        showTime1.setStartDateTime(LocalDateTime.of(2026, 5, 30, 9, 0));
        showTime1.setEndDateTime(LocalDateTime.of(2026, 5, 30, 11, 0));

        ShowTime showTime2 = new ShowTime();
        showTime2.setId(showtimeId2);
        showTime2.setFilmId(filmId);
        showTime2.setHallId(hallId2);
        showTime2.setPricingPolicyId(pricingPolicyId2);
        showTime2.setStatus(ShowTimeEnum.ShowTimeStatus.SCHEDULED);
        showTime2.setIsDeleted(false);
        showTime2.setStartDateTime(LocalDateTime.of(2026, 5, 30, 12, 0));
        showTime2.setEndDateTime(LocalDateTime.of(2026, 5, 30, 14, 0));

        FilmResponse filmResponse = FilmResponse.builder().id(filmId).build();
        HallResponse hallResponse1 = HallResponse.builder().id(hallId1).cinemaId(cinemaId).build();
        HallResponse hallResponse2 = HallResponse.builder().id(hallId2).cinemaId(cinemaId).build();
        PricingPolicy policy1 = new PricingPolicy();
        policy1.setId(pricingPolicyId1);
        PricingPolicy policy2 = new PricingPolicy();
        policy2.setId(pricingPolicyId2);
        PricingPolicyResponse policyResponse1 = PricingPolicyResponse.builder().id(pricingPolicyId1).build();
        PricingPolicyResponse policyResponse2 = PricingPolicyResponse.builder().id(pricingPolicyId2).build();
        SeatGrpcClient.LayoutBundle layoutBundle = SeatGrpcClient.LayoutBundle.builder()
                .totalRows(1)
                .totalCols(1)
                .screenPosition("TOP")
                .seats(List.of(
                        com.cinema.grpc.seat.LayoutSeatPayload.newBuilder()
                                .setSeatCode("A1")
                                .setRow(1)
                                .setCol(1)
                                .setSeatType("STANDARD")
                                .build()))
                .cells(List.of())
                .build();

        when(hallGrpcClient.listActiveHallIdsByCinema(cinemaId)).thenReturn(List.of(hallId1, hallId2));
        when(showTimeRepositoryImpl.countWithFilter(eq(null), any())).thenReturn(2L);
        when(showTimeRepositoryImpl.searchWithPageAndSortAndFilter(eq(null), eq(1), eq(20), any(), any()))
                .thenReturn(List.of(showTime1, showTime2));
        when(filmGrpcClient.getFilmsByIds(List.of(filmId))).thenReturn(Map.of(filmId, filmResponse));
        when(hallGrpcClient.getHallById(hallId1)).thenReturn(hallResponse1);
        when(hallGrpcClient.getHallById(hallId2)).thenReturn(hallResponse2);
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenReturn("Cinema Alpha");
        when(pricingPolicyRepository.findAllById(List.of(pricingPolicyId1, pricingPolicyId2)))
                .thenReturn(List.of(policy1, policy2));
        when(pricingPolicyMapper.toResponse(policy1)).thenReturn(policyResponse1);
        when(pricingPolicyMapper.toResponse(policy2)).thenReturn(policyResponse2);
        when(seatGrpcClient.getLayoutByHallId(hallId1)).thenReturn(layoutBundle);
        when(seatGrpcClient.getLayoutByHallId(hallId2)).thenReturn(layoutBundle);
        when(bookingGrpcClient.getSeatRuntimeStates(any(), any())).thenReturn(Map.of("A1", "AVAILABLE"));
        when(showTimeMapper.toResponse(showTime1)).thenReturn(ShowTimeResponse.builder()
                .id(showtimeId1)
                .hallId(hallId1)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId1)
                .startDateTime(showTime1.getStartDateTime())
                .endDateTime(showTime1.getEndDateTime())
                .status(showTime1.getStatus())
                .isDeleted(false)
                .timeCreated(showTime1.getTimeCreated())
                .timeUpdated(showTime1.getTimeUpdated())
                .build());
        when(showTimeMapper.toResponse(showTime2)).thenReturn(ShowTimeResponse.builder()
                .id(showtimeId2)
                .hallId(hallId2)
                .filmId(filmId)
                .pricingPolicyId(pricingPolicyId2)
                .startDateTime(showTime2.getStartDateTime())
                .endDateTime(showTime2.getEndDateTime())
                .status(showTime2.getStatus())
                .isDeleted(false)
                .timeCreated(showTime2.getTimeCreated())
                .timeUpdated(showTime2.getTimeUpdated())
                .build());

        var result = showTimeService.searchShowtimesByFilmId(filmId, request);

        assertNotNull(result);
        assertEquals(2, result.getData().size());
        assertEquals("Cinema Alpha", result.getData().get(0).getHall().getCinemaName());
        assertEquals("Cinema Alpha", result.getData().get(1).getHall().getCinemaName());
        verify(cinemaGrpcClient, times(1)).getCinemaNameById(cinemaId);
    }

    private HttpServletRequest managerRequest(UUID userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("MANAGER");
        when(request.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
        return request;
    }

    private HttpServletRequest adminRequest(UUID userId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HeaderNames.X_USER_ROLE)).thenReturn("ADMIN");
        return request;
    }

    private CinemaOperatingHoursResponse cinemaOperatingHours(UUID cinemaId, String openTime, String closeTime) {
        return CinemaOperatingHoursResponse.builder()
                .id(cinemaId)
                .name("Cinema Alpha")
                .openTime(LocalTime.parse(openTime))
                .closeTime(LocalTime.parse(closeTime))
                .build();
    }
}
