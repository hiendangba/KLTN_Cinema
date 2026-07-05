package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.dto.request.BookingRevenueField;
import com.cinema.booking_service.dto.request.BookingField;
import com.cinema.booking_service.dto.request.BookingRevenueReportRequest;
import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.CreateStaffBookingRequest;
import com.cinema.booking_service.dto.request.ShowtimePerformanceField;
import com.cinema.booking_service.dto.request.ShowtimePerformanceReportRequest;
import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.response.ActionMessageResponse;
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
import com.cinema.booking_service.grpc.IdentityGrpcClient;
import com.cinema.booking_service.grpc.HallGrpcClient;
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
import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
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
        private HallGrpcClient hallGrpcClient;

        @Mock
        private ShowtimeGrpcClient showtimeGrpcClient;

        @Mock
        private SeatGrpcClient seatGrpcClient;

        @Mock
        private SeatLockService seatLockService;

        @Mock
        private PaymentServiceClient paymentServiceClient;

        @Mock
        private IdentityGrpcClient identityGrpcClient;

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

                Booking booking1 = buildBooking(cinema1, film1, BookingStatus.PENDING, BigDecimal.valueOf(70000),
                                BigDecimal.ZERO);
                Booking booking2 = buildBooking(cinema1, film2, BookingStatus.CONFIRMED, BigDecimal.valueOf(80000),
                                BigDecimal.valueOf(20000));
                Booking booking3 = buildBooking(cinema2, film1, BookingStatus.RESERVED, BigDecimal.valueOf(90000),
                                BigDecimal.ZERO);

                when(bookingRepositoryImpl.findAllForBookingRevenueReport(anyCollection(), anyCollection(), any(),
                                any(), anyCollection()))
                                .thenAnswer(invocation -> {
                                        Collection<UUID> cinemaIds = invocation.getArgument(0);
                                        Collection<UUID> filmIds = invocation.getArgument(1);
                                        return List.of(booking1, booking2, booking3).stream()
                                                        .filter(booking -> cinemaIds.contains(booking.getCinemaId()))
                                                        .filter(booking -> filmIds == null || filmIds.isEmpty()
                                                                        || filmIds.contains(booking.getFilmId()))
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
                ArgumentCaptor<Collection<BookingStatus>> statusesCaptor = ArgumentCaptor.forClass(Collection.class);
                org.mockito.Mockito.verify(bookingRepositoryImpl).findAllForBookingRevenueReport(
                                cinemaIdsCaptor.capture(),
                                filmIdsCaptor.capture(),
                                any(),
                                any(),
                                statusesCaptor.capture());

                assertEquals(List.of(cinema1), List.copyOf(cinemaIdsCaptor.getValue()));
                assertEquals(List.of(film1), List.copyOf(filmIdsCaptor.getValue()));
                assertEquals(
                                EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED,
                                                BookingStatus.EXPIRED),
                                EnumSet.copyOf(statusesCaptor.getValue()));
                assertEquals(1L, response.total().totalBookings());
                assertEquals(1, response.items().size());
                assertEquals(cinema1, response.items().get(0).cinemaId());
                assertEquals(1L, response.items().get(0).totalBookings());
                assertEquals(1L, response.totalElements());
        }

        @Test
        void getAllCinemaRevenueReport_shouldCalculateConversionRateFromBookingCounts() {
                UUID cinema1 = UUID.randomUUID();
                UUID film1 = UUID.randomUUID();
                UUID film2 = UUID.randomUUID();

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));

                Booking booking1 = buildBooking(cinema1, film1, BookingStatus.CONFIRMED,
                                BigDecimal.valueOf(100000), BigDecimal.valueOf(50000));
                Booking booking2 = buildBooking(cinema1, film2, BookingStatus.RESERVED,
                                BigDecimal.valueOf(120000), BigDecimal.valueOf(30000));

                when(bookingRepositoryImpl.findAllForBookingRevenueReport(anyCollection(), any(), any(),
                                anyCollection()))
                                .thenReturn(List.of(booking1, booking2));

                BookingRevenueReportRequest request = BookingRevenueReportRequest.builder()
                                .pageRequest(PageRequest.<BookingRevenueField>builder()
                                                .page(1)
                                                .size(10)
                                                .build())
                                .build();

                BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);

                assertEquals(new BigDecimal("0.5000"), response.total().conversionRate());
                assertEquals(new BigDecimal("0.5000"), response.page().conversionRate());
                assertEquals(new BigDecimal("0.5000"), response.items().get(0).conversionRate());
        }

        @Test
        void getAllCinemaRevenueReport_shouldIncludeExpiredBookingsInCountsTotalsAndFiltering() {
                UUID cinema1 = UUID.randomUUID();
                UUID film1 = UUID.randomUUID();
                UUID film2 = UUID.randomUUID();
                UUID film3 = UUID.randomUUID();
                UUID film4 = UUID.randomUUID();

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));

                Booking expiredBooking = buildBooking(
                                cinema1, film1, BookingStatus.EXPIRED, BigDecimal.valueOf(70000), BigDecimal.valueOf(10000));
                Booking confirmedBooking = buildBooking(
                                cinema1, film2, BookingStatus.CONFIRMED, BigDecimal.valueOf(80000), BigDecimal.valueOf(5000));
                Booking reservedBooking = buildBooking(
                                cinema1, film3, BookingStatus.RESERVED, BigDecimal.valueOf(50000), BigDecimal.ZERO);
                Booking pendingBooking = buildBooking(
                                cinema1, film4, BookingStatus.PENDING, BigDecimal.valueOf(40000), BigDecimal.ZERO);

                when(bookingRepositoryImpl.findAllForBookingRevenueReport(anyCollection(), any(), any(), anyCollection()))
                                .thenReturn(List.of(expiredBooking, confirmedBooking, reservedBooking, pendingBooking));

                BookingRevenueReportRequest request = BookingRevenueReportRequest.builder()
                                .pageRequest(PageRequest.<BookingRevenueField>builder()
                                                .page(1)
                                                .size(10)
                                                .filterBy(List.of(
                                                                FilterField.<BookingRevenueField>builder()
                                                                                .field(BookingRevenueField.EXPIRED_COUNT)
                                                                                .operator("GTE")
                                                                                .value(1)
                                                                                .build()))
                                                .sortBy(List.of(
                                                                SortField.<BookingRevenueField>builder()
                                                                                .field(BookingRevenueField.EXPIRED_COUNT)
                                                                                .direction("DESC")
                                                                                .build()))
                                                .build())
                                .build();

                BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);

                assertEquals(1L, response.total().expiredCount());
                assertEquals(1L, response.page().expiredCount());
                assertEquals(4L, response.total().totalBookings());
                assertEquals(1, response.items().size());
                assertEquals(1L, response.items().get(0).expiredCount());
                assertEquals(new BigDecimal("0.2500"), response.total().conversionRate());
                assertEquals(new BigDecimal("0.2500"), response.page().conversionRate());
                assertEquals(new BigDecimal("0.2500"), response.items().get(0).conversionRate());
        }

        @Test
        void getAllCinemaRevenueReport_shouldSupportFilteringAndSortingByConversionRate() {
                UUID cinema1 = UUID.randomUUID();
                UUID cinema2 = UUID.randomUUID();

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"),
                                new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2")));

                when(bookingRepositoryImpl.findAllForBookingRevenueReport(anyCollection(), any(), any(), anyCollection()))
                                .thenReturn(List.of(
                                                buildBooking(cinema1, UUID.randomUUID(), BookingStatus.CONFIRMED, BigDecimal.TEN, BigDecimal.ZERO),
                                                buildBooking(cinema1, UUID.randomUUID(), BookingStatus.RESERVED, BigDecimal.TEN, BigDecimal.ZERO),
                                                buildBooking(cinema2, UUID.randomUUID(), BookingStatus.CONFIRMED, BigDecimal.TEN, BigDecimal.ZERO),
                                                buildBooking(cinema2, UUID.randomUUID(), BookingStatus.CONFIRMED, BigDecimal.TEN, BigDecimal.ZERO)));

                BookingRevenueReportRequest request = BookingRevenueReportRequest.builder()
                                .pageRequest(PageRequest.<BookingRevenueField>builder()
                                                .page(1)
                                                .size(10)
                                                .filterBy(List.of(
                                                                FilterField.<BookingRevenueField>builder()
                                                                                .field(BookingRevenueField.CONVERSION_RATE)
                                                                                .operator("GTE")
                                                                                .value("0.7000")
                                                                                .build()))
                                                .sortBy(List.of(
                                                                SortField.<BookingRevenueField>builder()
                                                                                .field(BookingRevenueField.CONVERSION_RATE)
                                                                                .direction("DESC")
                                                                                .build()))
                                                .build())
                                .build();

                BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);

                assertEquals(1, response.items().size());
                assertEquals(cinema2, response.items().get(0).cinemaId());
                assertEquals(new BigDecimal("1.0000"), response.items().get(0).conversionRate());
                assertEquals(new BigDecimal("1.0000"), response.page().conversionRate());
        }

        @Test
        void getAllCinemaRevenueReport_shouldAllowOnlyFromDate() {
                UUID cinema1 = UUID.randomUUID();
                LocalDateTime from = LocalDateTime.of(2026, 5, 29, 9, 30);

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
                when(bookingRepositoryImpl.findAllForBookingRevenueReport(anyCollection(), any(), any(),
                                anyCollection()))
                                .thenReturn(List.of());

                BookingRevenueReportRequest request = BookingRevenueReportRequest.builder()
                                .dateRange(DateRange.builder().from(from).build())
                                .pageRequest(PageRequest.<BookingRevenueField>builder()
                                                .page(1)
                                                .size(10)
                                                .build())
                                .build();

                BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);

                assertEquals(from, response.from());
                assertEquals(null, response.to());
                org.mockito.Mockito.verify(bookingRepositoryImpl).findAllForBookingRevenueReport(
                                anyCollection(),
                                eq(from),
                                isNull(),
                                anyCollection());
        }

        @Test
        void getAllCinemaRevenueReport_shouldAllowOnlyToDate() {
                UUID cinema1 = UUID.randomUUID();
                LocalDateTime to = LocalDateTime.of(2026, 5, 29, 18, 0);

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
                when(bookingRepositoryImpl.findAllForBookingRevenueReport(anyCollection(), any(), any(),
                                anyCollection()))
                                .thenReturn(List.of());

                BookingRevenueReportRequest request = BookingRevenueReportRequest.builder()
                                .dateRange(DateRange.builder().to(to).build())
                                .pageRequest(PageRequest.<BookingRevenueField>builder()
                                                .page(1)
                                                .size(10)
                                                .build())
                                .build();

                BookingRevenueReportResponse response = bookingService.getAllCinemaRevenueReport(request);

                assertEquals(null, response.from());
                assertEquals(to, response.to());
                org.mockito.Mockito.verify(bookingRepositoryImpl).findAllForBookingRevenueReport(
                                anyCollection(),
                                isNull(),
                                eq(to),
                                anyCollection());
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
                when(cinemaGrpcClient.getCinemaById(cinema1))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"));
                when(cinemaGrpcClient.getCinemaById(cinema2))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinema2, "Cinema 2"));

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

                when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), any(), any(), any(),
                                any()))
                                .thenReturn(List.of(booking1, booking2, booking3));

                when(showtimeGrpcClient.getShowtimeById(showtime1))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                                                .id(showtime1)
                                                .hallId(hall1)
                                                .cinemaId(cinema1)
                                                .pricingPolicyId(UUID.randomUUID())
                                                .filmId(film1)
                                                .startDateTime(LocalDateTime.of(2026, 5, 29, 18, 0))
                                                .endDateTime(LocalDateTime.of(2026, 5, 29, 20, 0))
                                                .build());
                when(showtimeGrpcClient.getShowtimeById(showtime2))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
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
                when(hallGrpcClient.getHallById(hall1))
                                .thenReturn(new HallGrpcClient.HallSummary(hall1, cinema1, "Phòng 1"));
                when(hallGrpcClient.getHallById(hall2))
                                .thenReturn(new HallGrpcClient.HallSummary(hall2, cinema2, "Phòng 2"));
                when(filmGrpcClient.getFilmById(film1))
                                .thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                                .id(film1)
                                                .title("Phim 1")
                                                .build());
                when(filmGrpcClient.getFilmById(film2))
                                .thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                                .id(film2)
                                                .title("Phim 2")
                                                .build());

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
                assertEquals(0, response.total().occupancyRate().compareTo(new BigDecimal("0.1667")));

                Map<UUID, ShowtimePerformanceItemResponse> itemByShowtimeId = response.items().stream()
                                .collect(Collectors.toMap(ShowtimePerformanceItemResponse::showtimeId, item -> item));
                assertEquals(2, itemByShowtimeId.size());
                assertEquals(2L, itemByShowtimeId.get(showtime1).totalBookings());
                assertEquals(3L, itemByShowtimeId.get(showtime1).totalSeatsBooked());
                assertEquals(10L, itemByShowtimeId.get(showtime1).totalSeatCapacity());
                assertEquals("Cinema 1", itemByShowtimeId.get(showtime1).cinemaName());
                assertEquals("Phim 1", itemByShowtimeId.get(showtime1).filmName());
                assertEquals("Phòng 1", itemByShowtimeId.get(showtime1).hallName());
                assertEquals(0, itemByShowtimeId.get(showtime1).occupancyRate().compareTo(new BigDecimal("0.3000")));
                assertEquals(1L, itemByShowtimeId.get(showtime2).totalBookings());
                assertEquals(2L, itemByShowtimeId.get(showtime2).totalSeatsBooked());
                assertEquals(20L, itemByShowtimeId.get(showtime2).totalSeatCapacity());
                assertEquals("Cinema 2", itemByShowtimeId.get(showtime2).cinemaName());
                assertEquals("Phim 2", itemByShowtimeId.get(showtime2).filmName());
                assertEquals("Phòng 2", itemByShowtimeId.get(showtime2).hallName());
                assertEquals(0, itemByShowtimeId.get(showtime2).occupancyRate().compareTo(new BigDecimal("0.1000")));
        }

        @Test
        void getAllShowtimePerformanceReport_shouldCalculateOccupancyAsRatio() {
                UUID cinema1 = UUID.randomUUID();
                UUID film1 = UUID.randomUUID();
                UUID showtime1 = UUID.randomUUID();
                UUID hall1 = UUID.randomUUID();

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
                when(cinemaGrpcClient.getCinemaById(cinema1))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"));

                Booking booking1 = buildShowtimeBooking(
                                showtime1, cinema1, film1, BookingStatus.RESERVED,
                                LocalDateTime.of(2026, 7, 3, 17, 20),
                                LocalDateTime.of(2026, 7, 3, 19, 39),
                                7);

                when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), any(), any(), any(),
                                any()))
                                .thenReturn(List.of(booking1));

                when(showtimeGrpcClient.getShowtimeById(showtime1))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                                                .id(showtime1)
                                                .hallId(hall1)
                                                .cinemaId(cinema1)
                                                .pricingPolicyId(UUID.randomUUID())
                                                .filmId(film1)
                                                .startDateTime(LocalDateTime.of(2026, 7, 3, 17, 20))
                                                .endDateTime(LocalDateTime.of(2026, 7, 3, 19, 39))
                                                .build());

                when(seatGrpcClient.getLayoutByHallId(hall1)).thenReturn(layoutBundle(hall1, 54));
                when(hallGrpcClient.getHallById(hall1))
                                .thenReturn(new HallGrpcClient.HallSummary(hall1, cinema1, "Phòng 06"));
                when(filmGrpcClient.getFilmById(film1))
                                .thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                                .id(film1)
                                                .title("VENOM: KẺ CUỐI")
                                                .build());

                ShowtimePerformanceReportRequest request = ShowtimePerformanceReportRequest.builder()
                                .pageRequest(PageRequest.<ShowtimePerformanceField>builder()
                                                .page(1)
                                                .size(10)
                                                .build())
                                .build();

                ShowtimePerformanceReportResponse response = bookingService.getAllShowtimePerformanceReport(request);

                assertEquals(0, response.total().occupancyRate().compareTo(new BigDecimal("0.1296")));
                assertEquals(0, response.items().get(0).occupancyRate().compareTo(new BigDecimal("0.1296")));
                assertEquals("Cinema 1", response.items().get(0).cinemaName());
                assertEquals("Phòng 06", response.items().get(0).hallName());
                assertEquals("VENOM: KẺ CUỐI", response.items().get(0).filmName());
        }

        @Test
        void getAllShowtimePerformanceReport_shouldResolveNamesOncePerUniqueEntity() {
                UUID cinema1 = UUID.randomUUID();
                UUID film1 = UUID.randomUUID();
                UUID showtime1 = UUID.randomUUID();
                UUID showtime2 = UUID.randomUUID();
                UUID hall1 = UUID.randomUUID();

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
                when(cinemaGrpcClient.getCinemaById(cinema1))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"));

                Booking booking1 = buildShowtimeBooking(
                                showtime1, cinema1, film1, BookingStatus.RESERVED,
                                LocalDateTime.of(2026, 7, 3, 17, 20),
                                LocalDateTime.of(2026, 7, 3, 19, 39),
                                2);
                Booking booking2 = buildShowtimeBooking(
                                showtime2, cinema1, film1, BookingStatus.CONFIRMED,
                                LocalDateTime.of(2026, 7, 3, 20, 0),
                                LocalDateTime.of(2026, 7, 3, 22, 0),
                                3);

                when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), any(), any(), any(),
                                any()))
                                .thenReturn(List.of(booking1, booking2));

                when(showtimeGrpcClient.getShowtimeById(showtime1))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                                                .id(showtime1)
                                                .hallId(hall1)
                                                .cinemaId(cinema1)
                                                .pricingPolicyId(UUID.randomUUID())
                                                .filmId(film1)
                                                .startDateTime(LocalDateTime.of(2026, 7, 3, 17, 20))
                                                .endDateTime(LocalDateTime.of(2026, 7, 3, 19, 39))
                                                .build());
                when(showtimeGrpcClient.getShowtimeById(showtime2))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                                                .id(showtime2)
                                                .hallId(hall1)
                                                .cinemaId(cinema1)
                                                .pricingPolicyId(UUID.randomUUID())
                                                .filmId(film1)
                                                .startDateTime(LocalDateTime.of(2026, 7, 3, 20, 0))
                                                .endDateTime(LocalDateTime.of(2026, 7, 3, 22, 0))
                                                .build());

                when(seatGrpcClient.getLayoutByHallId(hall1)).thenReturn(layoutBundle(hall1, 54));
                when(hallGrpcClient.getHallById(hall1))
                                .thenReturn(new HallGrpcClient.HallSummary(hall1, cinema1, "Phòng 06"));
                when(filmGrpcClient.getFilmById(film1))
                                .thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                                .id(film1)
                                                .title("VENOM: KẺ CUỐI")
                                                .build());

                ShowtimePerformanceReportRequest request = ShowtimePerformanceReportRequest.builder()
                                .pageRequest(PageRequest.<ShowtimePerformanceField>builder()
                                                .page(1)
                                                .size(10)
                                                .build())
                                .build();

                ShowtimePerformanceReportResponse response = bookingService.getAllShowtimePerformanceReport(request);

                assertEquals(2, response.items().size());
                org.mockito.Mockito.verify(cinemaGrpcClient).getCinemaById(cinema1);
                org.mockito.Mockito.verify(hallGrpcClient).getHallById(hall1);
                org.mockito.Mockito.verify(filmGrpcClient).getFilmById(film1);
        }

        @Test
        void exportShowtimePerformanceReport_shouldIgnorePageSizeAndExportAllRows() throws Exception {
                UUID cinema1 = UUID.randomUUID();
                UUID film1 = UUID.randomUUID();
                UUID showtime1 = UUID.randomUUID();
                UUID showtime2 = UUID.randomUUID();
                UUID hall1 = UUID.randomUUID();

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_ADMIN);
                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
                when(cinemaGrpcClient.getCinemaById(cinema1))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1"));

                Booking booking1 = buildShowtimeBooking(
                                showtime1, cinema1, film1, BookingStatus.RESERVED,
                                LocalDateTime.of(2026, 7, 3, 17, 20),
                                LocalDateTime.of(2026, 7, 3, 19, 39),
                                2);
                Booking booking2 = buildShowtimeBooking(
                                showtime2, cinema1, film1, BookingStatus.CONFIRMED,
                                LocalDateTime.of(2026, 7, 3, 20, 0),
                                LocalDateTime.of(2026, 7, 3, 22, 0),
                                3);

                when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), any(), any(), any(),
                                any()))
                                .thenReturn(List.of(booking1, booking2));

                when(showtimeGrpcClient.getShowtimeById(showtime1))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                                                .id(showtime1)
                                                .hallId(hall1)
                                                .cinemaId(cinema1)
                                                .pricingPolicyId(UUID.randomUUID())
                                                .filmId(film1)
                                                .startDateTime(LocalDateTime.of(2026, 7, 3, 17, 20))
                                                .endDateTime(LocalDateTime.of(2026, 7, 3, 19, 39))
                                                .build());
                when(showtimeGrpcClient.getShowtimeById(showtime2))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
                                                .id(showtime2)
                                                .hallId(hall1)
                                                .cinemaId(cinema1)
                                                .pricingPolicyId(UUID.randomUUID())
                                                .filmId(film1)
                                                .startDateTime(LocalDateTime.of(2026, 7, 3, 20, 0))
                                                .endDateTime(LocalDateTime.of(2026, 7, 3, 22, 0))
                                                .build());
                when(seatGrpcClient.getLayoutByHallId(hall1)).thenReturn(layoutBundle(hall1, 54));
                when(hallGrpcClient.getHallById(hall1))
                                .thenReturn(new HallGrpcClient.HallSummary(hall1, cinema1, "Phòng 06"));
                when(filmGrpcClient.getFilmById(film1))
                                .thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                                .id(film1)
                                                .title("VENOM: KẺ CUỐI")
                                                .build());

                ShowtimePerformanceReportRequest request = ShowtimePerformanceReportRequest.builder()
                                .pageRequest(PageRequest.<ShowtimePerformanceField>builder()
                                                .page(1)
                                                .size(1)
                                                .build())
                                .build();

                byte[] file = bookingService.exportShowtimePerformanceReport(request, httpRequest);

                try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                             new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(file))) {
                        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
                        assertTrue(sheet.getRow(0).getCell(0).getStringCellValue().length() > 0);
                        assertEquals(3, sheet.getLastRowNum());
                        assertEquals(showtime2.toString(), sheet.getRow(2).getCell(0).getStringCellValue());
                        assertEquals(showtime1.toString(), sheet.getRow(3).getCell(0).getStringCellValue());
                }
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
                when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), anyCollection(), any(),
                                any(), anyCollection()))
                                .thenAnswer(invocation -> {
                                        Collection<UUID> cinemaIds = invocation.getArgument(0);
                                        Collection<UUID> filmIds = invocation.getArgument(1);
                                        LocalDateTime from = invocation.getArgument(2);
                                        LocalDateTime to = invocation.getArgument(3);
                                        return allBookings.stream()
                                                        .filter(booking -> cinemaIds == null || cinemaIds.isEmpty()
                                                                        || cinemaIds.contains(booking.getCinemaId()))
                                                        .filter(booking -> filmIds == null || filmIds.isEmpty()
                                                                        || filmIds.contains(booking.getFilmId()))
                                                        .filter(booking -> from == null || !booking
                                                                        .getShowtimeStartDateTime().isBefore(from))
                                                        .filter(booking -> to == null || !booking
                                                                        .getShowtimeStartDateTime().isAfter(to))
                                                        .toList();
                                });

                when(showtimeGrpcClient.getShowtimeById(showtime1))
                                .thenReturn(ShowtimeGrpcClient.ShowtimeSummary.builder()
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

        @Test
        void getAllShowtimePerformanceReport_shouldAllowOnlyFromDate() {
                UUID cinema1 = UUID.randomUUID();
                LocalDateTime from = LocalDateTime.of(2026, 5, 29, 18, 0);

                when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                                new CinemaGrpcClient.CinemaSummary(cinema1, "Cinema 1")));
                when(bookingRepositoryImpl.findAllForShowtimePerformanceReport(anyCollection(), any(), any(), any(),
                                any()))
                                .thenReturn(List.of());

                ShowtimePerformanceReportRequest request = ShowtimePerformanceReportRequest.builder()
                                .dateRange(DateRange.builder().from(from).build())
                                .pageRequest(PageRequest.<ShowtimePerformanceField>builder()
                                                .page(1)
                                                .size(10)
                                                .build())
                                .build();

                ShowtimePerformanceReportResponse response = bookingService.getAllShowtimePerformanceReport(request);

                assertEquals(from, response.from());
                assertEquals(null, response.to());
                org.mockito.Mockito.verify(bookingRepositoryImpl).findAllForShowtimePerformanceReport(
                                anyCollection(),
                                isNull(),
                                eq(from),
                                isNull(),
                                anyCollection());
        }

        @Test
        @SuppressWarnings("unchecked")
        void searchMyActiveBookings_shouldForceActiveFiltersAndIgnoreClientStatusFilters() {
                UUID userId = UUID.randomUUID();
                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());

                when(bookingRepositoryImpl.countWithFilter(eq(userId), isNull(), isNull(), any()))
                                .thenReturn(0L);
                when(bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                                eq(userId), isNull(), isNull(), eq(1), eq(10), anyList(), any()))
                                .thenReturn(List.of());

                LocalDateTime before = LocalDateTime.now().minusSeconds(1);
                PageRequest<BookingField> request = PageRequest.<BookingField>builder()
                                .page(1)
                                .size(10)
                                .filterBy(List.of(
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.BOOKING_STATUS)
                                                                .operator("EQ")
                                                                .value(BookingStatus.CONFIRMED)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.PAYMENT_STATUS)
                                                                .operator("EQ")
                                                                .value(PaymentStatus.PAID)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.FILM_TITLE)
                                                                .operator("LIKE")
                                                                .value("Avatar")
                                                                .build()))
                                .build();

                bookingService.searchMyActiveBookings(request, httpRequest);
                LocalDateTime after = LocalDateTime.now().plusSeconds(1);

                ArgumentCaptor<List> countFilterCaptor = ArgumentCaptor.forClass(List.class);
                ArgumentCaptor<List> searchFilterCaptor = ArgumentCaptor.forClass(List.class);
                org.mockito.Mockito.verify(bookingRepositoryImpl).countWithFilter(
                                eq(userId),
                                isNull(),
                                isNull(),
                                countFilterCaptor.capture());
                org.mockito.Mockito.verify(bookingRepositoryImpl).searchWithPageAndSortAndFilter(
                                eq(userId),
                                isNull(),
                                isNull(),
                                eq(1),
                                eq(10),
                                anyList(),
                                searchFilterCaptor.capture());

                List<FilterField<BookingField>> countFilters = (List<FilterField<BookingField>>) countFilterCaptor
                                .getValue();
                List<FilterField<BookingField>> searchFilters = (List<FilterField<BookingField>>) searchFilterCaptor
                                .getValue();

                assertEquals(4, countFilters.size());
                assertEquals(BookingField.FILM_TITLE, countFilters.get(0).getField());
                assertEquals(BookingField.BOOKING_STATUS, countFilters.get(1).getField());
                assertEquals(List.of(BookingStatus.PENDING, BookingStatus.RESERVED), countFilters.get(1).getValue());
                assertEquals(BookingField.PAYMENT_STATUS, countFilters.get(2).getField());
                assertEquals(PaymentStatus.UNPAID, countFilters.get(2).getValue());
                assertEquals(BookingField.RESERVED_UNTIL, countFilters.get(3).getField());
                assertEquals("GTE", countFilters.get(3).getOperator());
                LocalDateTime reservedUntil = (LocalDateTime) countFilters.get(3).getValue();
                assertTrue(!reservedUntil.isBefore(before));
                assertTrue(!reservedUntil.isAfter(after));
                assertEquals(countFilters, searchFilters);
        }

        @Test
        @SuppressWarnings("unchecked")
        void searchMyBookingHistory_shouldForcePaidFiltersAndIgnoreClientStatusFilters() {
                UUID userId = UUID.randomUUID();
                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());

                when(bookingRepositoryImpl.countWithFilter(eq(userId), isNull(), isNull(), any()))
                                .thenReturn(0L);
                when(bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                                eq(userId), isNull(), isNull(), eq(1), eq(10), anyList(), any()))
                                .thenReturn(List.of());

                PageRequest<BookingField> request = PageRequest.<BookingField>builder()
                                .page(1)
                                .size(10)
                                .filterBy(List.of(
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.BOOKING_STATUS)
                                                                .operator("EQ")
                                                                .value(BookingStatus.EXPIRED)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.PAYMENT_STATUS)
                                                                .operator("EQ")
                                                                .value(PaymentStatus.UNPAID)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.FILM_TITLE)
                                                                .operator("LIKE")
                                                                .value("Avatar")
                                                                .build()))
                                .build();

                bookingService.searchMyBookingHistory(request, httpRequest);

                ArgumentCaptor<List> countFilterCaptor = ArgumentCaptor.forClass(List.class);
                ArgumentCaptor<List> searchFilterCaptor = ArgumentCaptor.forClass(List.class);
                org.mockito.Mockito.verify(bookingRepositoryImpl).countWithFilter(
                                eq(userId),
                                isNull(),
                                isNull(),
                                countFilterCaptor.capture());
                org.mockito.Mockito.verify(bookingRepositoryImpl).searchWithPageAndSortAndFilter(
                                eq(userId),
                                isNull(),
                                isNull(),
                                eq(1),
                                eq(10),
                                anyList(),
                                searchFilterCaptor.capture());

                List<FilterField<BookingField>> countFilters = (List<FilterField<BookingField>>) countFilterCaptor
                                .getValue();
                List<FilterField<BookingField>> searchFilters = (List<FilterField<BookingField>>) searchFilterCaptor
                                .getValue();

                assertEquals(3, countFilters.size());
                assertEquals(BookingField.FILM_TITLE, countFilters.get(0).getField());
                assertEquals(BookingField.BOOKING_STATUS, countFilters.get(1).getField());
                assertEquals(BookingStatus.CONFIRMED, countFilters.get(1).getValue());
                assertEquals(BookingField.PAYMENT_STATUS, countFilters.get(2).getField());
                assertEquals(PaymentStatus.PAID, countFilters.get(2).getValue());
                assertEquals(countFilters, searchFilters);
        }

        @Test
        @SuppressWarnings("unchecked")
        void searchPurchasedBookingsByOperatorCinema_shouldForcePurchasedFiltersAndIgnoreClientStatusFilters() {
                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_ADMIN);

                when(bookingRepositoryImpl.countWithFilter(isNull(), isNull(), isNull(), any()))
                                .thenReturn(0L);
                when(bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                                isNull(), isNull(), isNull(), eq(1), eq(10), anyList(), any()))
                                .thenReturn(List.of());

                PageRequest<BookingField> request = PageRequest.<BookingField>builder()
                                .page(1)
                                .size(10)
                                .filterBy(List.of(
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.BOOKING_STATUS)
                                                                .operator("EQ")
                                                                .value(BookingStatus.EXPIRED)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.PAYMENT_STATUS)
                                                                .operator("EQ")
                                                                .value(PaymentStatus.UNPAID)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.FILM_TITLE)
                                                                .operator("LIKE")
                                                                .value("Avatar")
                                                                .build()))
                                .build();

                bookingService.searchPurchasedBookingsByOperatorCinema(request, httpRequest);

                ArgumentCaptor<List> countFilterCaptor = ArgumentCaptor.forClass(List.class);
                ArgumentCaptor<List> searchFilterCaptor = ArgumentCaptor.forClass(List.class);
                org.mockito.Mockito.verify(bookingRepositoryImpl).countWithFilter(
                                isNull(),
                                isNull(),
                                isNull(),
                                countFilterCaptor.capture());
                org.mockito.Mockito.verify(bookingRepositoryImpl).searchWithPageAndSortAndFilter(
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(1),
                                eq(10),
                                anyList(),
                                searchFilterCaptor.capture());

                List<FilterField<BookingField>> countFilters = (List<FilterField<BookingField>>) countFilterCaptor
                                .getValue();
                List<FilterField<BookingField>> searchFilters = (List<FilterField<BookingField>>) searchFilterCaptor
                                .getValue();

                assertEquals(3, countFilters.size());
                assertEquals(BookingField.FILM_TITLE, countFilters.get(0).getField());
                assertEquals(BookingField.BOOKING_STATUS, countFilters.get(1).getField());
                assertEquals(BookingStatus.CONFIRMED, countFilters.get(1).getValue());
                assertEquals(BookingField.PAYMENT_STATUS, countFilters.get(2).getField());
                assertEquals(PaymentStatus.PAID, countFilters.get(2).getValue());
                assertEquals(countFilters, searchFilters);
        }

        @Test
        @SuppressWarnings("unchecked")
        void searchUnpaidBookingsByOperatorCinema_shouldForceUnpaidFiltersAndIgnoreClientStatusFilters() {
                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_MANAGER);
                UUID userId = UUID.randomUUID();
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());

                UUID cinemaId = UUID.randomUUID();
                when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER))
                                .thenReturn(List.of(cinemaId));
                when(bookingRepositoryImpl.countWithFilter(isNull(), anyCollection(), isNull(), any()))
                                .thenReturn(0L);
                when(bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                                isNull(), anyCollection(), isNull(), eq(1), eq(10), anyList(), any()))
                                .thenReturn(List.of());

                PageRequest<BookingField> request = PageRequest.<BookingField>builder()
                                .page(1)
                                .size(10)
                                .filterBy(List.of(
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.BOOKING_STATUS)
                                                                .operator("EQ")
                                                                .value(BookingStatus.CONFIRMED)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.PAYMENT_STATUS)
                                                                .operator("EQ")
                                                                .value(PaymentStatus.PAID)
                                                                .build(),
                                                FilterField.<BookingField>builder()
                                                                .field(BookingField.FILM_TITLE)
                                                                .operator("LIKE")
                                                                .value("Avatar")
                                                                .build()))
                                .build();

                bookingService.searchUnpaidBookingsByOperatorCinema(request, httpRequest);

                ArgumentCaptor<Collection<UUID>> cinemaIdsCaptor = ArgumentCaptor.forClass(Collection.class);
                ArgumentCaptor<List> countFilterCaptor = ArgumentCaptor.forClass(List.class);
                ArgumentCaptor<List> searchFilterCaptor = ArgumentCaptor.forClass(List.class);
                org.mockito.Mockito.verify(bookingRepositoryImpl).countWithFilter(
                                isNull(),
                                cinemaIdsCaptor.capture(),
                                isNull(),
                                countFilterCaptor.capture());
                org.mockito.Mockito.verify(bookingRepositoryImpl).searchWithPageAndSortAndFilter(
                                isNull(),
                                cinemaIdsCaptor.capture(),
                                isNull(),
                                eq(1),
                                eq(10),
                                anyList(),
                                searchFilterCaptor.capture());

                List<FilterField<BookingField>> countFilters = (List<FilterField<BookingField>>) countFilterCaptor
                                .getValue();
                List<FilterField<BookingField>> searchFilters = (List<FilterField<BookingField>>) searchFilterCaptor
                                .getValue();

                assertEquals(List.of(cinemaId), List.copyOf(cinemaIdsCaptor.getAllValues().get(0)));
                assertEquals(3, countFilters.size());
                assertEquals(BookingField.FILM_TITLE, countFilters.get(0).getField());
                assertEquals(BookingField.BOOKING_STATUS, countFilters.get(1).getField());
                assertEquals("IN", countFilters.get(1).getOperator());
                assertEquals(List.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.EXPIRED),
                                countFilters.get(1).getValue());
                assertEquals(BookingField.PAYMENT_STATUS, countFilters.get(2).getField());
                assertEquals(PaymentStatus.UNPAID, countFilters.get(2).getValue());
                assertEquals(countFilters, searchFilters);
        }

        @Test
        void getBookingById_shouldForbidCustomerWhenBookingIsNotPaid() {
                UUID userId = UUID.randomUUID();
                UUID bookingId = UUID.randomUUID();
                Booking booking = buildBooking(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                BookingStatus.EXPIRED,
                                BigDecimal.valueOf(100000),
                                BigDecimal.ZERO);
                booking.setId(bookingId);
                booking.setUserId(userId);
                booking.setPaymentStatus(PaymentStatus.UNPAID);

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));

                BusinessException exception = assertThrows(BusinessException.class,
                                () -> bookingService.getBookingById(bookingId, httpRequest));

                assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        }

        @Test
        void getBookingById_shouldReturnHallName() {
                UUID userId = UUID.randomUUID();
                UUID bookingId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID showtimeId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                Booking booking = buildBooking(
                                cinemaId,
                                filmId,
                                BookingStatus.CONFIRMED,
                                BigDecimal.valueOf(100000),
                                BigDecimal.ZERO);
                booking.setId(bookingId);
                booking.setUserId(userId);
                booking.setShowtimeId(showtimeId);
                booking.setPaymentStatus(PaymentStatus.PAID);

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));
                when(bookingMapper.toResponse(booking)).thenReturn(BookingResponse.builder()
                                .id(bookingId)
                                .cinemaId(cinemaId)
                                .paymentStatus(PaymentStatus.PAID)
                                .bookingStatus(BookingStatus.CONFIRMED)
                                .build());
                when(cinemaGrpcClient.getCinemaById(cinemaId))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema Star"));
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(hallGrpcClient.getHallById(hallId))
                                .thenReturn(new HallGrpcClient.HallSummary(hallId, cinemaId, "Hall 1"));

                BookingResponse response = bookingService.getBookingById(bookingId, httpRequest);

                assertEquals("Hall 1", response.getHallName());
                assertEquals("Cinema Star", response.getCinemaName());
        }

        @Test
        void searchMyActiveBookings_shouldIncludeHallNameInResponse() {
                UUID userId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID showtimeId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                Booking booking = buildBooking(
                                cinemaId,
                                filmId,
                                BookingStatus.RESERVED,
                                BigDecimal.valueOf(100000),
                                BigDecimal.ZERO);
                booking.setShowtimeId(showtimeId);

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(bookingRepositoryImpl.countWithFilter(eq(userId), isNull(), isNull(), any()))
                                .thenReturn(1L);
                when(bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                                eq(userId), isNull(), isNull(), eq(1), eq(10), anyList(), any()))
                                .thenReturn(List.of(booking));
                when(bookingMapper.toResponse(booking)).thenReturn(BookingResponse.builder()
                                .id(booking.getId())
                                .cinemaId(cinemaId)
                                .bookingStatus(BookingStatus.RESERVED)
                                .paymentStatus(PaymentStatus.UNPAID)
                                .build());
                when(cinemaGrpcClient.getCinemaById(cinemaId))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema Star"));
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(hallGrpcClient.getHallById(hallId))
                                .thenReturn(new HallGrpcClient.HallSummary(hallId, cinemaId, "Hall A"));

                var response = bookingService.searchMyActiveBookings(
                                PageRequest.<BookingField>builder().page(1).size(10).build(), httpRequest);

                assertEquals(1, response.getData().size());
                assertEquals("Hall A", response.getData().get(0).getHallName());
        }

        @Test
        void searchMyBookingHistory_shouldIncludeHallNameInResponse() {
                UUID userId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID showtimeId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                Booking booking = buildBooking(
                                cinemaId,
                                filmId,
                                BookingStatus.CONFIRMED,
                                BigDecimal.valueOf(100000),
                                BigDecimal.ZERO);
                booking.setShowtimeId(showtimeId);
                booking.setPaymentStatus(PaymentStatus.PAID);

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(bookingRepositoryImpl.countWithFilter(eq(userId), isNull(), isNull(), any()))
                                .thenReturn(1L);
                when(bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                                eq(userId), isNull(), isNull(), eq(1), eq(10), anyList(), any()))
                                .thenReturn(List.of(booking));
                when(bookingMapper.toResponse(booking)).thenReturn(BookingResponse.builder()
                                .id(booking.getId())
                                .cinemaId(cinemaId)
                                .bookingStatus(BookingStatus.CONFIRMED)
                                .paymentStatus(PaymentStatus.PAID)
                                .build());
                when(cinemaGrpcClient.getCinemaById(cinemaId))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema Star"));
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(hallGrpcClient.getHallById(hallId))
                                .thenReturn(new HallGrpcClient.HallSummary(hallId, cinemaId, "Hall B"));

                var response = bookingService.searchMyBookingHistory(
                                PageRequest.<BookingField>builder().page(1).size(10).build(), httpRequest);

                assertEquals(1, response.getData().size());
                assertEquals("Hall B", response.getData().get(0).getHallName());
        }

        @Test
        void getCheckoutContext_shouldAllowCustomerOwnerWhenBookingIsActiveAndUnpaid() {
                UUID userId = UUID.randomUUID();
                UUID bookingId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID showtimeId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                Booking booking = buildBooking(
                                cinemaId,
                                filmId,
                                BookingStatus.RESERVED,
                                BigDecimal.valueOf(100000),
                                BigDecimal.ZERO);
                booking.setId(bookingId);
                booking.setUserId(userId);
                booking.setShowtimeId(showtimeId);
                booking.setReservedUntil(LocalDateTime.now().plusMinutes(10));
                booking.setBookingStatus(BookingStatus.RESERVED);
                booking.setPaymentStatus(PaymentStatus.UNPAID);

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));
                when(paymentServiceClient.getSessionByBookingId(eq(bookingId), eq(userId))).thenReturn(null);
                when(bookingMapper.toResponse(booking)).thenReturn(BookingResponse.builder()
                                .id(bookingId)
                                .cinemaId(cinemaId)
                                .bookingStatus(BookingStatus.RESERVED)
                                .paymentStatus(PaymentStatus.UNPAID)
                                .build());
                when(cinemaGrpcClient.getCinemaById(cinemaId))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema Star"));
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(hallGrpcClient.getHallById(hallId))
                                .thenReturn(new HallGrpcClient.HallSummary(hallId, cinemaId, "Hall Prime"));

                var response = bookingService.getCheckoutContext(bookingId, httpRequest);

                assertNotNull(response);
                assertEquals("Cinema Star", response.getBooking().getCinemaName());
                assertEquals("Hall Prime", response.getBooking().getHallName());
                assertTrue(response.isCanPay());
        }

        @Test
        void createBooking_shouldReturnHallName() {
                UUID userId = UUID.randomUUID();
                UUID showtimeId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID seatId = UUID.randomUUID();

                CreateBookingRequest request = new CreateBookingRequest();
                request.setShowtimeId(showtimeId);
                request.setCinemaId(cinemaId);

                CreateBookingRequest.CustomerInfo customerInfo = new CreateBookingRequest.CustomerInfo();
                customerInfo.setFullName("Test User");
                customerInfo.setEmail("test@example.com");
                customerInfo.setPhone("0123456789");
                request.setCustomerInfo(customerInfo);

                CreateBookingRequest.SeatItem seatItem = new CreateBookingRequest.SeatItem();
                seatItem.setSeatCode("A1");
                seatItem.setSeatType(com.cinema.Enum.HallEnum.SeatType.STANDARD);
                seatItem.setSeatPriceSnapshot(BigDecimal.valueOf(70000));
                request.setSeatItems(List.of(seatItem));
                request.setProductItems(List.of());

                Booking mappedBooking = new Booking();
                mappedBooking.setShowtimeId(showtimeId);
                mappedBooking.setCustomerInfo(new CustomerInfo());
                mappedBooking.setProductItems(new ArrayList<>());
                mappedBooking.setSeatItems(new ArrayList<>());

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(filmGrpcClient.getFilmById(filmId)).thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                .id(filmId)
                                .title("Film A")
                                .build());
                when(seatGrpcClient.getSeatSnapshotsByCodes(hallId, List.of("A1")))
                                .thenReturn(Map.of("A1", new SeatGrpcClient.SeatSnapshot(seatId,
                                                com.cinema.Enum.HallEnum.SeatType.STANDARD)));
                when(bookingSeatItemRepository.existsLockedSeatCodes(showtimeId, List.of("A1"), EnumSet.of(
                                BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED)))
                                .thenReturn(false);
                when(bookingMapper.toEntity(request)).thenReturn(mappedBooking);
                when(bookingSeatItemMapper.toEntity(any(CreateBookingRequest.SeatItem.class)))
                                .thenAnswer(invocation -> {
                                        CreateBookingRequest.SeatItem seatItemRequest = invocation.getArgument(0);
                                        BookingSeatItem seatItemEntity = new BookingSeatItem();
                                        seatItemEntity.setSeatPriceSnapshot(seatItemRequest.getSeatPriceSnapshot());
                                        return seatItemEntity;
                                });
                when(seatLockService.tryLockSeats(eq(showtimeId), eq(List.of("A1")), any(UUID.class), any()))
                                .thenReturn(true);
                when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

                ActionMessageResponse response = bookingService.createBooking(request, httpRequest);

                assertEquals(SuccessMessage.BOOKING_CREATED.getMessage(), response.getMessage());
        }

        @Test
        void createBooking_shouldReturnSeatAlreadyLockedWhenSeatWasHeldByAnotherBooking() {
                UUID userId = UUID.randomUUID();
                UUID showtimeId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID seatId = UUID.randomUUID();

                CreateBookingRequest request = new CreateBookingRequest();
                request.setShowtimeId(showtimeId);
                request.setCinemaId(cinemaId);

                CreateBookingRequest.CustomerInfo customerInfo = new CreateBookingRequest.CustomerInfo();
                customerInfo.setFullName("Test User");
                customerInfo.setEmail("test@example.com");
                customerInfo.setPhone("0123456789");
                request.setCustomerInfo(customerInfo);

                CreateBookingRequest.SeatItem seatItem = new CreateBookingRequest.SeatItem();
                seatItem.setSeatCode("A1");
                seatItem.setSeatType(com.cinema.Enum.HallEnum.SeatType.STANDARD);
                seatItem.setSeatPriceSnapshot(BigDecimal.valueOf(70000));
                request.setSeatItems(List.of(seatItem));
                request.setProductItems(List.of());

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(filmGrpcClient.getFilmById(filmId)).thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                .id(filmId)
                                .title("Film A")
                                .build());
                when(seatGrpcClient.getSeatSnapshotsByCodes(hallId, List.of("A1")))
                                .thenReturn(Map.of("A1", new SeatGrpcClient.SeatSnapshot(seatId,
                                                com.cinema.Enum.HallEnum.SeatType.STANDARD)));
                when(bookingSeatItemRepository.existsLockedSeatCodes(showtimeId, List.of("A1"), EnumSet.of(
                                BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED)))
                                .thenReturn(true);

                BusinessException ex = assertThrows(BusinessException.class,
                                () -> bookingService.createBooking(request, httpRequest));
                assertEquals(ErrorCode.SEAT_ALREADY_LOCKED, ex.getErrorCode());
        }

        @Test
        void createStaffBooking_shouldCreateCustomerAndBookingWhenCustomerIdIsNull() {
                UUID showtimeId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID seatId = UUID.randomUUID();
                UUID operatorId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();

                CreateStaffBookingRequest request = new CreateStaffBookingRequest();
                request.setCustomerId(null);
                request.setShowtimeId(showtimeId);
                request.setCinemaId(cinemaId);

                CreateBookingRequest.CustomerInfo customerInfo = new CreateBookingRequest.CustomerInfo();
                customerInfo.setFullName("Walk-in Customer");
                customerInfo.setEmail("walkin@example.com");
                customerInfo.setPhone("0912345678");
                request.setCustomerInfo(customerInfo);

                CreateBookingRequest.SeatItem seatItem = new CreateBookingRequest.SeatItem();
                seatItem.setSeatCode("A1");
                seatItem.setSeatType(com.cinema.Enum.HallEnum.SeatType.STANDARD);
                seatItem.setSeatPriceSnapshot(BigDecimal.valueOf(70000));
                request.setSeatItems(List.of(seatItem));
                request.setProductItems(List.of());

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_STAFF);
                when(identityGrpcClient.createCustomer(any(CreateBookingRequest.CustomerInfo.class))).thenReturn(customerId);
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(filmGrpcClient.getFilmById(filmId)).thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                .id(filmId)
                                .title("Film A")
                                .build());
                when(seatGrpcClient.getSeatSnapshotsByCodes(hallId, List.of("A1")))
                                .thenReturn(Map.of("A1", new SeatGrpcClient.SeatSnapshot(seatId,
                                                com.cinema.Enum.HallEnum.SeatType.STANDARD)));
                when(bookingSeatItemRepository.existsLockedSeatCodes(showtimeId, List.of("A1"), EnumSet.of(
                                BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED)))
                                .thenReturn(false);
                when(bookingMapper.toEntity(any(CreateBookingRequest.class))).thenReturn(new Booking());
                when(bookingSeatItemMapper.toEntity(any(CreateBookingRequest.SeatItem.class)))
                                .thenAnswer(invocation -> {
                                        CreateBookingRequest.SeatItem seatItemRequest = invocation.getArgument(0);
                                        BookingSeatItem seatItemEntity = new BookingSeatItem();
                                        seatItemEntity.setSeatPriceSnapshot(seatItemRequest.getSeatPriceSnapshot());
                                        return seatItemEntity;
                                });
                when(seatLockService.tryLockSeats(eq(showtimeId), eq(List.of("A1")), any(UUID.class), any()))
                                .thenReturn(true);
                when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

                ActionMessageResponse response = bookingService.createStaffBooking(request, httpRequest);

                assertEquals(SuccessMessage.BOOKING_CREATED.getMessage(), response.getMessage());
                org.mockito.Mockito.verify(identityGrpcClient).createCustomer(any(CreateBookingRequest.CustomerInfo.class));
        }

        @Test
        void createStaffBooking_shouldValidateExistingCustomerWhenCustomerIdIsProvided() {
                UUID showtimeId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID seatId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();

                CreateStaffBookingRequest request = new CreateStaffBookingRequest();
                request.setCustomerId(customerId);
                request.setShowtimeId(showtimeId);
                request.setCinemaId(cinemaId);

                CreateBookingRequest.CustomerInfo customerInfo = new CreateBookingRequest.CustomerInfo();
                customerInfo.setFullName("Existing Customer");
                customerInfo.setEmail("existing@example.com");
                customerInfo.setPhone("0912345678");
                request.setCustomerInfo(customerInfo);

                CreateBookingRequest.SeatItem seatItem = new CreateBookingRequest.SeatItem();
                seatItem.setSeatCode("A1");
                seatItem.setSeatType(com.cinema.Enum.HallEnum.SeatType.STANDARD);
                seatItem.setSeatPriceSnapshot(BigDecimal.valueOf(70000));
                request.setSeatItems(List.of(seatItem));
                request.setProductItems(List.of());

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_STAFF);
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(filmGrpcClient.getFilmById(filmId)).thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                .id(filmId)
                                .title("Film A")
                                .build());
                when(seatGrpcClient.getSeatSnapshotsByCodes(hallId, List.of("A1")))
                                .thenReturn(Map.of("A1", new SeatGrpcClient.SeatSnapshot(seatId,
                                                com.cinema.Enum.HallEnum.SeatType.STANDARD)));
                when(bookingSeatItemRepository.existsLockedSeatCodes(showtimeId, List.of("A1"), EnumSet.of(
                                BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED)))
                                .thenReturn(false);
                when(bookingMapper.toEntity(any(CreateBookingRequest.class))).thenReturn(new Booking());
                when(bookingSeatItemMapper.toEntity(any(CreateBookingRequest.SeatItem.class)))
                                .thenAnswer(invocation -> {
                                        CreateBookingRequest.SeatItem seatItemRequest = invocation.getArgument(0);
                                        BookingSeatItem seatItemEntity = new BookingSeatItem();
                                        seatItemEntity.setSeatPriceSnapshot(seatItemRequest.getSeatPriceSnapshot());
                                        return seatItemEntity;
                                });
                when(seatLockService.tryLockSeats(eq(showtimeId), eq(List.of("A1")), any(UUID.class), any()))
                                .thenReturn(true);
                when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

                ActionMessageResponse response = bookingService.createStaffBooking(request, httpRequest);

                assertEquals(SuccessMessage.BOOKING_CREATED.getMessage(), response.getMessage());
                org.mockito.Mockito.verify(identityGrpcClient).requireCustomerExists(customerId);
        }

        @Test
        void createStaffBooking_shouldDeleteCreatedCustomerWhenBookingFails() {
                UUID showtimeId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID seatId = UUID.randomUUID();
                UUID operatorId = UUID.randomUUID();
                CreateStaffBookingRequest request = new CreateStaffBookingRequest();
                request.setCustomerId(null);
                request.setShowtimeId(showtimeId);
                request.setCinemaId(cinemaId);

                CreateBookingRequest.CustomerInfo customerInfo = new CreateBookingRequest.CustomerInfo();
                customerInfo.setFullName("Walk-in Customer");
                customerInfo.setEmail("walkin@example.com");
                customerInfo.setPhone("0912345678");
                request.setCustomerInfo(customerInfo);

                CreateBookingRequest.SeatItem seatItem = new CreateBookingRequest.SeatItem();
                seatItem.setSeatCode("A1");
                seatItem.setSeatType(com.cinema.Enum.HallEnum.SeatType.STANDARD);
                seatItem.setSeatPriceSnapshot(BigDecimal.valueOf(70000));
                request.setSeatItems(List.of(seatItem));
                request.setProductItems(List.of());

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_STAFF);
                when(identityGrpcClient.createCustomer(any(CreateBookingRequest.CustomerInfo.class)))
                                .thenReturn(UUID.randomUUID());
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(filmGrpcClient.getFilmById(filmId)).thenReturn(FilmGrpcClient.FilmSnapshot.builder()
                                .id(filmId)
                                .title("Film A")
                                .build());
                when(seatGrpcClient.getSeatSnapshotsByCodes(hallId, List.of("A1")))
                                .thenReturn(Map.of("A1", new SeatGrpcClient.SeatSnapshot(seatId,
                                                com.cinema.Enum.HallEnum.SeatType.STANDARD)));
                when(bookingSeatItemRepository.existsLockedSeatCodes(showtimeId, List.of("A1"), EnumSet.of(
                                BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED)))
                                .thenReturn(false);
                when(bookingMapper.toEntity(any(CreateBookingRequest.class))).thenReturn(new Booking());
                when(bookingSeatItemMapper.toEntity(any(CreateBookingRequest.SeatItem.class)))
                                .thenAnswer(invocation -> {
                                        CreateBookingRequest.SeatItem seatItemRequest = invocation.getArgument(0);
                                        BookingSeatItem seatItemEntity = new BookingSeatItem();
                                        seatItemEntity.setSeatPriceSnapshot(seatItemRequest.getSeatPriceSnapshot());
                                        return seatItemEntity;
                                });
                when(seatLockService.tryLockSeats(eq(showtimeId), eq(List.of("A1")), any(UUID.class), any()))
                                .thenReturn(true);
                when(bookingRepository.save(any(Booking.class))).thenThrow(new RuntimeException("db error"));

                assertThrows(RuntimeException.class, () -> bookingService.createStaffBooking(request, httpRequest));
                org.mockito.Mockito.verify(identityGrpcClient).deleteCustomer(any(UUID.class));
        }

        @Test
        void getBookingById_shouldReturnNullHallNameWhenHallLookupFails() {
                UUID userId = UUID.randomUUID();
                UUID bookingId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                UUID filmId = UUID.randomUUID();
                UUID showtimeId = UUID.randomUUID();
                UUID hallId = UUID.randomUUID();
                Booking booking = buildBooking(
                                cinemaId,
                                filmId,
                                BookingStatus.CONFIRMED,
                                BigDecimal.valueOf(100000),
                                BigDecimal.ZERO);
                booking.setId(bookingId);
                booking.setUserId(userId);
                booking.setShowtimeId(showtimeId);
                booking.setPaymentStatus(PaymentStatus.PAID);

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
                when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));
                when(bookingMapper.toResponse(booking)).thenReturn(BookingResponse.builder()
                                .id(bookingId)
                                .cinemaId(cinemaId)
                                .paymentStatus(PaymentStatus.PAID)
                                .bookingStatus(BookingStatus.CONFIRMED)
                                .build());
                when(cinemaGrpcClient.getCinemaById(cinemaId))
                                .thenReturn(new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema Star"));
                when(showtimeGrpcClient.getShowtimeById(showtimeId))
                                .thenReturn(showtimeSummary(showtimeId, hallId, cinemaId, filmId));
                when(hallGrpcClient.getHallById(hallId)).thenThrow(new BusinessException(ErrorCode.HALL_NOT_FOUND));

                BookingResponse response = bookingService.getBookingById(bookingId, httpRequest);

                assertEquals(null, response.getHallName());
        }

        @Test
        void getCheckoutContext_shouldForbidCustomerWhoDoesNotOwnBooking() {
                UUID ownerId = UUID.randomUUID();
                UUID otherUserId = UUID.randomUUID();
                UUID bookingId = UUID.randomUUID();
                UUID cinemaId = UUID.randomUUID();
                Booking booking = buildBooking(
                                cinemaId,
                                UUID.randomUUID(),
                                BookingStatus.RESERVED,
                                BigDecimal.valueOf(100000),
                                BigDecimal.ZERO);
                booking.setId(bookingId);
                booking.setUserId(ownerId);
                booking.setReservedUntil(LocalDateTime.now().plusMinutes(10));
                booking.setPaymentStatus(PaymentStatus.UNPAID);

                when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_CUSTOMER);
                when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(otherUserId.toString());
                when(bookingRepository.findByIdAndIsDeletedFalse(bookingId)).thenReturn(Optional.of(booking));

                BusinessException exception = assertThrows(BusinessException.class,
                                () -> bookingService.getCheckoutContext(bookingId, httpRequest));

                assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
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

        private ShowtimeGrpcClient.ShowtimeSummary showtimeSummary(UUID showtimeId, UUID hallId, UUID cinemaId,
                        UUID filmId) {
                return ShowtimeGrpcClient.ShowtimeSummary.builder()
                                .id(showtimeId)
                                .hallId(hallId)
                                .cinemaId(cinemaId)
                                .pricingPolicyId(UUID.randomUUID())
                                .filmId(filmId)
                                .startDateTime(LocalDateTime.of(2026, 5, 29, 18, 0))
                                .endDateTime(LocalDateTime.of(2026, 5, 29, 20, 0))
                                .build();
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

        @SuppressWarnings({ "rawtypes", "unchecked" })
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
