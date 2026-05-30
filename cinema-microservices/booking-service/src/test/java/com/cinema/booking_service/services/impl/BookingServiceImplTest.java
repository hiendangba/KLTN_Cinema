package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.dto.request.BookingRevenueField;
import com.cinema.booking_service.dto.request.BookingRevenueReportRequest;
import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.ShowtimePerformanceField;
import com.cinema.booking_service.dto.request.ShowtimePerformanceReportRequest;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.dto.response.BookingRevenueReportResponse;
import com.cinema.booking_service.dto.response.ShowtimePerformanceItemResponse;
import com.cinema.booking_service.dto.response.ShowtimePerformanceReportResponse;
import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.entity.BookingSeatItem;
import com.cinema.booking_service.entity.CustomerInfo;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.booking_service.grpc.CinemaGrpcClient;
import com.cinema.booking_service.grpc.FilmGrpcClient;
import com.cinema.booking_service.grpc.SeatGrpcClient;
import com.cinema.booking_service.grpc.ShowtimeGrpcClient;
import com.cinema.booking_service.http.PaymentServiceClient;
import com.cinema.booking_service.mapper.BookingMapper;
import com.cinema.booking_service.mapper.BookingSeatItemMapper;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.repository.BookingRepositoryImpl;
import com.cinema.booking_service.repository.BookingSeatItemRepository;
import com.cinema.booking_service.repository.ProductRepository;
import com.cinema.booking_service.services.SeatLockService;
import com.cinema.dto.request.PageRequest;
import com.cinema.http.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingRepositoryImpl bookingRepositoryImpl;

    @Mock
    private BookingSeatItemRepository bookingSeatItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private BookingSeatItemMapper bookingSeatItemMapper;

    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @Mock
    private FilmGrpcClient filmGrpcClient;

    @Mock
    private ShowtimeGrpcClient showtimeGrpcClient;

    @Mock
    private SeatGrpcClient seatGrpcClient;

    @Mock
    private SeatLockService seatLockService;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private BookingServiceImpl bookingService;

    @Test
    void getAllCinemaRevenueReport_shouldFilterByCinemaAndFilmBeforeAggregating() {
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"),
                new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2")));

        Booking booking1 = buildBooking(cinema1, film1, BookingStatus.PENDING, BigDecimal.valueOf(70000), BigDecimal.ZERO);
        Booking booking2 = buildBooking(cinema1, film2, BookingStatus.CONFIRMED, BigDecimal.valueOf(80000), BigDecimal.valueOf(20000));
        Booking booking3 = buildBooking(cinema2, film1, BookingStatus.RESERVED, BigDecimal.valueOf(90000), BigDecimal.ZERO);

        when(bookingRepository.findAllForBookingRevenueReport(anyCollection(), anyCollection(), any(), any(), anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<UUID> cinemaIds = invocation.getArgument(0);
                    Collection<UUID> filmIds = invocation.getArgument(1);
                    return List.of(booking1, booking2, booking3).stream()
                            .filter(booking -> cinemaIds.contains(booking.getCinemaId()))
                            .filter(booking -> filmIds == null || filmIds.isEmpty() || filmIds.contains(booking.getFilmId()))
                            .toList();
                });

        BookingRevenueReportRequest request = BookingRevenueReportRequest.builder()
                .cinemaIds(List.of(cinema1))
                .filmIds(List.of(film1))
                .pageRequest(PageRequest.<BookingRevenueField>builder()
                        .page(1)
                        .size(10)
                        .build())
                .build();

        BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);

        ArgumentCaptor<Collection<UUID>> cinemaIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<UUID>> filmIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(bookingRepository).findAllForBookingRevenueReport(
                cinemaIdsCaptor.capture(),
                filmIdsCaptor.capture(),
                any(),
                any(),
                anyCollection());

        assertEquals(List.of(cinema1), List.copyOf(cinemaIdsCaptor.getValue()));
        assertEquals(List.of(film1), List.copyOf(filmIdsCaptor.getValue()));
        assertEquals(1L, response.total().totalBookings());
        assertEquals(1, response.items().size());
        assertEquals(cinema1, response.items().get(0).cinemaId());
        assertEquals(1L, response.items().get(0).totalBookings());
        assertEquals(1L, response.totalElements());
    }

    @Test
    void getAllCinemaRevenueReport_shouldAggregatePromotionDiscountAndPayableAmount() {
        UUID cinema1 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));

        Booking booking1 = buildBooking(cinema1, film1, BookingStatus.CONFIRMED,
                BigDecimal.valueOf(100000), BigDecimal.valueOf(50000));
        booking1.setPromotionDiscountAmount(BigDecimal.valueOf(10000));
        booking1.setPayableAmount(BigDecimal.valueOf(140000));

        Booking booking2 = buildBooking(cinema1, film2, BookingStatus.RESERVED,
                BigDecimal.valueOf(120000), BigDecimal.valueOf(30000));
        booking2.setPromotionDiscountAmount(BigDecimal.valueOf(5000));
        booking2.setPayableAmount(BigDecimal.valueOf(145000));

        when(bookingRepository.findAllForBookingRevenueReport(anyCollection(), any(), any(), anyCollection()))
                .thenReturn(List.of(booking1, booking2));

        BookingRevenueReportRequest request = BookingRevenueReportRequest.builder()
                .pageRequest(PageRequest.<BookingRevenueField>builder()
                        .page(1)
                        .size(10)
                        .build())
                .build();

        BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);

        assertEquals(BigDecimal.valueOf(300000), response.total().grossAmount());
        assertEquals(BigDecimal.valueOf(15000), response.total().promotionDiscountAmount());
        assertEquals(BigDecimal.valueOf(285000), response.total().payableAmount());
        assertEquals(BigDecimal.valueOf(15000), response.items().get(0).promotionDiscountAmount());
        assertEquals(BigDecimal.valueOf(285000), response.items().get(0).payableAmount());
    }

    @Test
    void getAllShowtimePerformanceReport_shouldGroupByShowtimeAndCalculateOccupancy() {
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();
        UUID showtime1 = UUID.randomUUID();
        UUID showtime2 = UUID.randomUUID();
        UUID hall1 = UUID.randomUUID();
        UUID hall2 = UUID.randomUUID();

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"),
                new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2")));

        Booking booking1 = buildShowtimeBooking(
                showtime1, cinema1, film1, BookingStatus.RESERVED,
                LocalDateTime.of(2026, 5, 29, 18, 0),
                LocalDateTime.of(2026, 5, 29, 20, 0),
                2);
        Booking booking2 = buildShowtimeBooking(
                showtime1, cinema1, film1, BookingStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 29, 18, 0),
                LocalDateTime.of(2026, 5, 29, 20, 0),
                1);
        Booking booking3 = buildShowtimeBooking(
                showtime2, cinema2, film2, BookingStatus.PENDING,
                LocalDateTime.of(2026, 5, 29, 21, 0),
                LocalDateTime.of(2026, 5, 29, 23, 0),
                2);

        when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), any(), any(), any(), any()))
                .thenReturn(List.of(booking1, booking2, booking3));

        when(showtimeGrpcClient.getShowtimeById(showtime1)).thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                .id(showtime1)
                .hallId(hall1)
                .cinemaId(cinema1)
                .pricingPolicyId(UUID.randomUUID())
                .filmId(film1)
                .startDateTime(LocalDateTime.of(2026, 5, 29, 18, 0))
                .endDateTime(LocalDateTime.of(2026, 5, 29, 20, 0))
                .build());
        when(showtimeGrpcClient.getShowtimeById(showtime2)).thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                .id(showtime2)
                .hallId(hall2)
                .cinemaId(cinema2)
                .pricingPolicyId(UUID.randomUUID())
                .filmId(film2)
                .startDateTime(LocalDateTime.of(2026, 5, 29, 21, 0))
                .endDateTime(LocalDateTime.of(2026, 5, 29, 23, 0))
                .build());

        when(seatGrpcClient.getLayoutByHallId(hall1)).thenReturn(layoutBundle(hall1, 10));
        when(seatGrpcClient.getLayoutByHallId(hall2)).thenReturn(layoutBundle(hall2, 20));

        ShowtimePerformanceReportRequest request = ShowtimePerformanceReportRequest.builder()
                .pageRequest(PageRequest.<ShowtimePerformanceField>builder()
                        .page(1)
                        .size(10)
                        .build())
                .build();

        ShowtimePerformanceReportResponse response = bookingService.getAllShowtimePerformanceReport(request);

        assertEquals(2L, response.totalElements());
        assertEquals(2L, response.total().totalShowtimes());
        assertEquals(3L, response.total().totalBookings());
        assertEquals(5L, response.total().totalSeatsBooked());
        assertEquals(30L, response.total().totalSeatCapacity());
        assertEquals(0, response.total().occupancyRate().compareTo(new BigDecimal("16.67")));

        Map<UUID, ShowtimePerformanceItemResponse> itemByShowtimeId = response.items().stream()
                .collect(Collectors.toMap(ShowtimePerformanceItemResponse::showtimeId, item -> item));
        assertEquals(2, itemByShowtimeId.size());
        assertEquals(2L, itemByShowtimeId.get(showtime1).totalBookings());
        assertEquals(3L, itemByShowtimeId.get(showtime1).totalSeatsBooked());
        assertEquals(10L, itemByShowtimeId.get(showtime1).totalSeatCapacity());
        assertEquals(0, itemByShowtimeId.get(showtime1).occupancyRate().compareTo(new BigDecimal("30.00")));
        assertEquals(1L, itemByShowtimeId.get(showtime2).totalBookings());
        assertEquals(2L, itemByShowtimeId.get(showtime2).totalSeatsBooked());
        assertEquals(20L, itemByShowtimeId.get(showtime2).totalSeatCapacity());
        assertEquals(0, itemByShowtimeId.get(showtime2).occupancyRate().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void getAllShowtimePerformanceReport_shouldFilterByCinemaAndFilmBeforeAggregating() {
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();
        UUID showtime1 = UUID.randomUUID();
        UUID showtime2 = UUID.randomUUID();
        UUID showtime3 = UUID.randomUUID();
        UUID hall1 = UUID.randomUUID();
        UUID hall2 = UUID.randomUUID();
        UUID hall3 = UUID.randomUUID();

        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"),
                new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2")));

        Booking booking1 = buildShowtimeBooking(
                showtime1, cinema1, film1, BookingStatus.RESERVED,
                LocalDateTime.of(2026, 5, 29, 18, 0),
                LocalDateTime.of(2026, 5, 29, 20, 0),
                2);
        Booking booking2 = buildShowtimeBooking(
                showtime1, cinema1, film1, BookingStatus.CONFIRMED,
                LocalDateTime.of(2026, 5, 29, 18, 0),
                LocalDateTime.of(2026, 5, 29, 20, 0),
                1);
        Booking booking3 = buildShowtimeBooking(
                showtime2, cinema1, film2, BookingStatus.PENDING,
                LocalDateTime.of(2026, 5, 29, 21, 0),
                LocalDateTime.of(2026, 5, 29, 23, 0),
                2);
        Booking booking4 = buildShowtimeBooking(
                showtime3, cinema2, film1, BookingStatus.RESERVED,
                LocalDateTime.of(2026, 5, 29, 21, 0),
                LocalDateTime.of(2026, 5, 29, 23, 0),
                1);

        List<Booking> allBookings = List.of(booking1, booking2, booking3, booking4);
        when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), anyCollection(), any(), any(), anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<UUID> cinemaIds = invocation.getArgument(0);
                    Collection<UUID> filmIds = invocation.getArgument(1);
                    LocalDateTime from = invocation.getArgument(2);
                    LocalDateTime to = invocation.getArgument(3);
                    return allBookings.stream()
                            .filter(booking -> cinemaIds == null || cinemaIds.isEmpty() || cinemaIds.contains(booking.getCinemaId()))
                            .filter(booking -> filmIds == null || filmIds.isEmpty() || filmIds.contains(booking.getFilmId()))
                            .filter(booking -> from == null || !booking.getShowtimeStartDateTime().isBefore(from))
                            .filter(booking -> to == null || !booking.getShowtimeStartDateTime().isAfter(to))
                            .toList();
                });

        when(showtimeGrpcClient.getShowtimeById(showtime1)).thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                .id(showtime1)
                .hallId(hall1)
                .cinemaId(cinema1)
                .pricingPolicyId(UUID.randomUUID())
                .filmId(film1)
                .startDateTime(LocalDateTime.of(2026, 5, 29, 18, 0))
                .endDateTime(LocalDateTime.of(2026, 5, 29, 20, 0))
                .build());
        when(seatGrpcClient.getLayoutByHallId(hall1)).thenReturn(layoutBundle(hall1, 10));

        ShowtimePerformanceReportRequest request = ShowtimePerformanceReportRequest.builder()
                .cinemaIds(List.of(cinema1))
                .filmIds(List.of(film1))
                .pageRequest(PageRequest.<ShowtimePerformanceField>builder()
                        .page(1)
                        .size(10)
                        .build())
                .build();

        ShowtimePerformanceReportResponse response = bookingService.getAllShowtimePerformanceReport(request);

        ArgumentCaptor<Collection<UUID>> cinemaIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Collection<UUID>> filmIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        org.mockito.Mockito.verify(bookingRepositoryImpl).findAllForShowtimePerformanceReport(
                cinemaIdsCaptor.capture(),
                filmIdsCaptor.capture(),
                any(),
                any(),
                anyCollection());

        assertEquals(List.of(cinema1), List.copyOf(cinemaIdsCaptor.getValue()));
        assertEquals(List.of(film1), List.copyOf(filmIdsCaptor.getValue()));
        assertEquals(1L, response.totalElements());
        assertEquals(1L, response.total().totalShowtimes());
        assertEquals(2L, response.total().totalBookings());
        assertEquals(3L, response.total().totalSeatsBooked());
        assertEquals(10L, response.total().totalSeatCapacity());
        assertEquals(1, response.items().size());
        assertEquals(showtime1, response.items().get(0).showtimeId());
    }

    private Booking buildBooking(
            UUID cinemaId,
            UUID filmId,
            BookingStatus status,
            BigDecimal ticketSubtotal,
            BigDecimal productSubtotal) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCinemaId(cinemaId);
        booking.setFilmId(filmId);
        booking.setBookingStatus(status);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setTicketSubtotal(ticketSubtotal);
        booking.setProductSubtotal(productSubtotal);
        booking.setFinalAmount(ticketSubtotal.add(productSubtotal));
        booking.setIsDeleted(false);
        booking.setTimeCreated(LocalDateTime.of(2026, 5, 29, 10, 0));
        booking.setSeatItems(List.of());
        booking.setProductItems(List.of());
        return booking;
    }

    private Booking buildShowtimeBooking(
            UUID showtimeId,
            UUID cinemaId,
            UUID filmId,
            BookingStatus status,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            int seatCount) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setShowtimeId(showtimeId);
        booking.setCinemaId(cinemaId);
        booking.setFilmId(filmId);
        booking.setShowtimeStartDateTime(startDateTime);
        booking.setShowtimeEndDateTime(endDateTime);
        booking.setBookingStatus(status);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setTicketSubtotal(BigDecimal.valueOf(70000L * seatCount));
        booking.setProductSubtotal(BigDecimal.ZERO);
        booking.setFinalAmount(booking.getTicketSubtotal());
        booking.setIsDeleted(false);
        booking.setTimeCreated(LocalDateTime.of(2026, 5, 29, 10, 0));
        booking.setSeatItems(new ArrayList<>());
        for (int i = 0; i < seatCount; i++) {
            booking.getSeatItems().add(new BookingSeatItem());
        }
        booking.setProductItems(List.of());
        return booking;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private SeatGrpcClient.LayoutBundle layoutBundle(UUID hallId, int capacity) {
        List<Object> seats = new ArrayList<>(Collections.nCopies(capacity, null));
        return SeatGrpcClient.LayoutBundle.builder()
                .totalRows(1)
                .totalCols(capacity)
                .screenPosition("TOP")
                .seats((List) seats)
                .cells(List.of())
                .build();
    }
}
