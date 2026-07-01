package com.cinema.booking_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.CreateStaffBookingRequest;
import com.cinema.booking_service.dto.request.BookingField;
import com.cinema.booking_service.dto.request.BookingRevenueField;
import com.cinema.booking_service.dto.request.BookingRevenueReportRequest;
import com.cinema.booking_service.dto.request.ShowtimePerformanceField;
import com.cinema.booking_service.dto.request.ShowtimePerformanceReportRequest;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.dto.response.BookingRevenueItemResponse;
import com.cinema.booking_service.dto.response.BookingRevenueReportResponse;
import com.cinema.booking_service.dto.response.BookingRevenueSummaryResponse;
import com.cinema.booking_service.dto.response.CheckoutContextResponse;
import com.cinema.booking_service.dto.response.PaymentSessionSnapshotResponse;
import com.cinema.booking_service.dto.response.ShowtimePerformanceItemResponse;
import com.cinema.booking_service.dto.response.ShowtimePerformanceReportResponse;
import com.cinema.booking_service.dto.response.ShowtimePerformanceSummaryResponse;
import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.entity.BookingProductItem;
import com.cinema.booking_service.entity.BookingSeatItem;
import com.cinema.booking_service.entity.Product;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.grpc.CinemaGrpcClient;
import com.cinema.booking_service.grpc.FilmGrpcClient;
import com.cinema.booking_service.grpc.IdentityGrpcClient;
import com.cinema.booking_service.grpc.HallGrpcClient;
import com.cinema.booking_service.http.PaymentServiceClient;
import com.cinema.booking_service.grpc.SeatGrpcClient;
import com.cinema.booking_service.grpc.ShowtimeGrpcClient;
import com.cinema.booking_service.mapper.BookingMapper;
import com.cinema.booking_service.mapper.BookingSeatItemMapper;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.repository.BookingRepositoryImpl;
import com.cinema.booking_service.repository.BookingSeatItemRepository;
import com.cinema.booking_service.repository.ProductRepository;
import com.cinema.booking_service.services.BookingService;
import com.cinema.booking_service.services.SeatLockService;
import com.cinema.Enum.HallEnum;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.request.DateRange;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.excel.ExcelExportUtils;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.text.SearchTextUtils;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.lang.reflect.Array;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingRepositoryImpl bookingRepositoryImpl;
    private final BookingSeatItemRepository bookingSeatItemRepository;
    private final ProductRepository productRepository;
    private final BookingMapper bookingMapper;
    private final BookingSeatItemMapper bookingSeatItemMapper;
    private final CinemaGrpcClient cinemaGrpcClient;
    private final FilmGrpcClient filmGrpcClient;
    private final HallGrpcClient hallGrpcClient;
    private final ShowtimeGrpcClient showtimeGrpcClient;
    private final SeatGrpcClient seatGrpcClient;
    private final SeatLockService seatLockService;
    private final PaymentServiceClient paymentServiceClient;
    private final IdentityGrpcClient identityGrpcClient;

    @Value("${booking.seat-lock-minutes:5}")
    private long seatLockMinutes;

    @Override
    @Transactional
    public ActionMessageResponse createBooking(CreateBookingRequest request, HttpServletRequest httpRequest) {
        validateBookingCreatorRole(httpRequest);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID userId = HeaderNames.ROLE_CUSTOMER.equals(role) ? resolveUserId(httpRequest) : null;

        return createBookingInternal(request, userId);
    }

    @Override
    @Transactional
    public ActionMessageResponse createStaffBooking(CreateStaffBookingRequest request, HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);

        UUID customerId = request.getCustomerId();
        boolean createdCustomer = false;
        UUID bookingUserId = customerId;
        if (bookingUserId == null) {
            bookingUserId = identityGrpcClient.createCustomer(request.getCustomerInfo());
            createdCustomer = true;
        } else {
            identityGrpcClient.requireCustomerExists(bookingUserId);
        }

        try {
            return createBookingInternal(request, bookingUserId);
        } catch (RuntimeException ex) {
            if (createdCustomer) {
                try {
                    identityGrpcClient.deleteCustomer(bookingUserId);
                } catch (RuntimeException cleanupEx) {
                    log.warn("Failed to clean up identity customer after booking failure: customerId={}",
                            bookingUserId, cleanupEx);
                }
            }
            throw ex;
        }
    }

    private ActionMessageResponse createBookingInternal(CreateBookingRequest request, UUID userId) {

        List<CreateBookingRequest.SeatItem> seatItems = request.getSeatItems();
        if (seatItems == null || seatItems.isEmpty() || seatItems.size() > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<String> normalizedSeatCodes = normalizeAndValidateSeatCodes(seatItems);
        ShowtimeGrpcClient.ShowtimeSummary showtime = showtimeGrpcClient.getShowtimeById(request.getShowtimeId());
        if (!showtime.getCinemaId().equals(request.getCinemaId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        FilmGrpcClient.FilmSnapshot film = filmGrpcClient.getFilmById(showtime.getFilmId());
        Map<String, SeatGrpcClient.SeatSnapshot> canonicalSeatSnapshotByCode =
                validateAndResolveSeatSnapshots(showtime.getHallId(), seatItems, normalizedSeatCodes);
        if (bookingSeatItemRepository.existsLockedSeatCodes(
                request.getShowtimeId(),
                normalizedSeatCodes,
                EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Booking booking = bookingMapper.toEntity(request);
        if (booking.getId() == null) {
            booking.setId(UuidCreator.getTimeOrderedEpoch());
        }
        booking.setUserId(userId);
        booking.setCinemaId(showtime.getCinemaId());
        booking.setFilmId(showtime.getFilmId());
        booking.setFilmTitle(film.getTitle());
        booking.setShowtimeStartDateTime(showtime.getStartDateTime());
        booking.setShowtimeEndDateTime(showtime.getEndDateTime());
        booking.setBookingStatus(BookingStatus.RESERVED);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setReservedUntil(LocalDateTime.now().plusMinutes(seatLockMinutes));

        List<BookingSeatItem> persistedSeatItems = buildSeatItems(
                booking,
                seatItems,
                normalizedSeatCodes,
                canonicalSeatSnapshotByCode);
        booking.setSeatItems(persistedSeatItems);
        BigDecimal ticketSubtotal = persistedSeatItems.stream()
                .map(BookingSeatItem::getSeatPriceSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        booking.setTicketSubtotal(ticketSubtotal);

        List<BookingProductItem> persistedProductItems = buildProductItems(
                booking,
                request.getProductItems(),
                showtime.getCinemaId());
        booking.setProductItems(persistedProductItems);
        BigDecimal productSubtotal = persistedProductItems.stream()
                .map(BookingProductItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        booking.setProductSubtotal(productSubtotal);

        booking.setFinalAmount(ticketSubtotal.add(productSubtotal));
        booking.setPromotionDiscountAmount(BigDecimal.ZERO);
        booking.setPayableAmount(booking.getFinalAmount());

        Duration ttl = Duration.ofMinutes(seatLockMinutes);
        boolean locked = seatLockService.tryLockSeats(request.getShowtimeId(), normalizedSeatCodes, booking.getId(), ttl);
        if (!locked) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        try {
            bookingRepository.save(booking);
            return ActionMessageResponse.builder()
                    .message(SuccessMessage.BOOKING_CREATED.getMessage())
                    .build();
        } catch (RuntimeException ex) {
            seatLockService.releaseSeats(request.getShowtimeId(), normalizedSeatCodes);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID id, HttpServletRequest httpRequest) {
        Booking booking = getActiveBookingOrThrow(id);
        authorizeBookingRead(booking, httpRequest);
        return toBookingResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchMyActiveBookings(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);
        UUID userId = resolveUserId(httpRequest);
        return searchBookingsByScope(userId, null, request, buildActiveBookingFilters(LocalDateTime.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchMyBookingHistory(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);
        UUID userId = resolveUserId(httpRequest);
        return searchBookingsByScope(userId, null, request, buildPurchasedBookingFilters());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchBookingsByOperatorCinema(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        return searchBookingsByOperatorCinema(request, httpRequest, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchPurchasedBookingsByOperatorCinema(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        return searchBookingsByOperatorCinema(request, httpRequest, buildPurchasedBookingFilters());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchUnpaidBookingsByOperatorCinema(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        return searchBookingsByOperatorCinema(request, httpRequest, buildUnpaidBookingFilters());
    }

    @Override
    @Transactional(readOnly = true)
    public BookingRevenueReportResponse getAllCinemaRevenueReport(BookingRevenueReportRequest request) {
        validateRevenueReportRequest(request);
        List<CinemaGrpcClient.CinemaSummary> cinemas = cinemaGrpcClient.getAllActiveCinemas();
        return buildBookingRevenueReport(cinemas, request);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingRevenueReportResponse getMyCinemaRevenueReport(
            BookingRevenueReportRequest request,
            HttpServletRequest httpRequest) {
        validateRevenueReportRequest(request);
        UUID requesterUserId = resolveUserId(httpRequest);
        List<UUID> accessibleCinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(
                requesterUserId,
                HeaderNames.ROLE_MANAGER);
        if (accessibleCinemaIds == null || accessibleCinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
        }
        List<CinemaGrpcClient.CinemaSummary> cinemas = cinemaGrpcClient.getAllActiveCinemas().stream()
                .filter(cinema -> cinema != null
                        && cinema.id() != null
                && accessibleCinemaIds.contains(cinema.id()))
                .toList();
        return buildBookingRevenueReport(cinemas, request);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCinemaRevenueReport(BookingRevenueReportRequest request, HttpServletRequest httpRequest) {
        validateRevenueReportRequest(request);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        List<CinemaGrpcClient.CinemaSummary> cinemas;
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            cinemas = cinemaGrpcClient.getAllActiveCinemas();
        } else if (HeaderNames.ROLE_MANAGER.equals(role)) {
            UUID requesterUserId = resolveUserId(httpRequest);
            List<UUID> accessibleCinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(
                    requesterUserId,
                    HeaderNames.ROLE_MANAGER);
            if (accessibleCinemaIds == null || accessibleCinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            cinemas = cinemaGrpcClient.getAllActiveCinemas().stream()
                    .filter(cinema -> cinema != null
                            && cinema.id() != null
                            && accessibleCinemaIds.contains(cinema.id()))
                    .toList();
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<BookingRevenueItemResponse> items = filterSelectedBookingRevenueItems(
                aggregateBookingRevenueItems(cinemas, request),
                request.getSelectedIds());

        return ExcelExportUtils.exportSingleSheet(
                "Báo cáo hiệu suất",
                "BÁO CÁO HIỆU SUẤT BOOKING THEO RẠP",
                List.of(
                        "Mã rạp",
                        "Tên rạp",
                        "Tổng booking",
                        "Số booking chờ xử lý",
                        "Số booking giữ chỗ",
                        "Số booking đã xác nhận",
                        "Số booking hết hạn",
                        "Tỷ lệ chuyển đổi"),
                items.stream()
                        .map(item -> Arrays.asList(
                                item.cinemaId(),
                                item.cinemaName(),
                                item.totalBookings(),
                                item.pendingCount(),
                                item.reservedCount(),
                                item.confirmedCount(),
                                item.expiredCount(),
                                item.conversionRate()))
                        .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ShowtimePerformanceReportResponse getAllShowtimePerformanceReport(ShowtimePerformanceReportRequest request) {
        validateShowtimePerformanceReportRequest(request);
        List<CinemaGrpcClient.CinemaSummary> cinemas = cinemaGrpcClient.getAllActiveCinemas();
        return buildShowtimePerformanceReport(cinemas, request);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowtimePerformanceReportResponse getMyShowtimePerformanceReport(
            ShowtimePerformanceReportRequest request,
            HttpServletRequest httpRequest) {
        validateShowtimePerformanceReportRequest(request);
        UUID requesterUserId = resolveUserId(httpRequest);
        List<UUID> accessibleCinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(
                requesterUserId,
                HeaderNames.ROLE_MANAGER);
        if (accessibleCinemaIds == null || accessibleCinemaIds.isEmpty()) {
            throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
        }
        List<CinemaGrpcClient.CinemaSummary> cinemas = cinemaGrpcClient.getAllActiveCinemas().stream()
                .filter(cinema -> cinema != null
                        && cinema.id() != null
                        && accessibleCinemaIds.contains(cinema.id()))
                .toList();
        return buildShowtimePerformanceReport(cinemas, request);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutContextResponse getCheckoutContext(UUID id, HttpServletRequest httpRequest) {
        Booking booking = getActiveBookingOrThrow(id);
        authorizeBookingCheckoutRead(booking, httpRequest);

        LocalDateTime now = LocalDateTime.now();
        long secondsToExpire = booking.getReservedUntil() == null
                ? 0L
                : Math.max(0L, ChronoUnit.SECONDS.between(now, booking.getReservedUntil()));

        PaymentSessionSnapshotResponse paymentSession = null;
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (HeaderNames.ROLE_CUSTOMER.equals(role)) {
            UUID requesterUserId = resolveUserId(httpRequest);
            paymentSession = paymentServiceClient.getSessionByBookingId(id, requesterUserId);
        }

        boolean activeStatus = booking.getBookingStatus() == BookingStatus.PENDING
                || booking.getBookingStatus() == BookingStatus.RESERVED;
        boolean notExpired = booking.getReservedUntil() != null && booking.getReservedUntil().isAfter(now);
        boolean unpaid = booking.getPaymentStatus() != PaymentStatus.PAID;
        boolean paymentSessionAllowsPay = paymentSession == null || isPayableSessionStatus(paymentSession.getStatus());

        return CheckoutContextResponse.builder()
                .booking(toBookingResponse(booking))
                .paymentSession(paymentSession)
                .secondsToExpire(secondsToExpire)
                .canPay(activeStatus && notExpired && unpaid && paymentSessionAllowsPay)
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse cancelBooking(UUID id, HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);
        UUID userId = resolveUserId(httpRequest);

        Booking booking = getActiveBookingOrThrow(id);
        if (!userId.equals(booking.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (booking.getBookingStatus() == BookingStatus.CONFIRMED && booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        seatLockService.releaseSeats(booking.getShowtimeId(), extractSeatCodes(booking.getSeatItems()));

        return ActionMessageResponse.builder()
                .message(SuccessMessage.BOOKING_CANCELLED.getMessage())
                .build();
    }

    private Booking getActiveBookingOrThrow(UUID id) {
        return bookingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private PageResponse<BookingResponse> searchBookingsByOperatorCinema(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest,
            List<FilterField<BookingField>> forcedFilters) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        UUID userId = null;
        Set<UUID> accessibleCinemaIds = null;
        if (!HeaderNames.ROLE_ADMIN.equals(role)) {
            accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest, role);
        }
        return searchBookingsByScope(userId, accessibleCinemaIds, request, forcedFilters);
    }

    private PageResponse<BookingResponse> searchBookingsByScope(
            UUID userId,
            Collection<UUID> accessibleCinemaIds,
            PageRequest<BookingField> request,
            List<FilterField<BookingField>> forcedFilters) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        String keyword = request.getNormalizedKeyword();

        List<SortField<BookingField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        if (sortFields.stream().noneMatch(sort -> sort != null && sort.getField() == BookingField.TIME_CREATED)) {
            sortFields.add(new SortField<>(BookingField.TIME_CREATED, "DESC"));
        }

        List<FilterField<BookingField>> filterBy = mergeForcedBookingFilters(request.getFilterBy(), forcedFilters);
        long totalElements = bookingRepositoryImpl.countWithFilter(
                userId, accessibleCinemaIds, keyword, filterBy);
        List<Booking> bookings = bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                userId, accessibleCinemaIds, keyword, page, size, sortFields, filterBy);
        Map<UUID, String> cinemaNameCache = new LinkedHashMap<>();
        Map<UUID, String> hallNameCache = new LinkedHashMap<>();
        Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> showtimeCache = prefetchShowtimeSummariesForBookingResponses(
                bookings.stream()
                        .map(Booking::getShowtimeId)
                        .filter(java.util.Objects::nonNull)
                        .toList());
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return PageResponse.<BookingResponse>builder()
                .data(bookings.stream()
                        .map(booking -> toBookingResponse(booking, cinemaNameCache, hallNameCache, showtimeCache))
                        .toList())
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return toBookingResponse(booking, new LinkedHashMap<>(), new LinkedHashMap<>(), Map.of());
    }

    private BookingResponse toBookingResponse(
            Booking booking,
            Map<UUID, String> cinemaNameCache,
            Map<UUID, String> hallNameCache,
            Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> showtimeCache) {
        BookingResponse response = bookingMapper.toResponse(booking);
        if (response == null) {
            return null;
        }
        response.setCinemaName(resolveCinemaName(booking == null ? null : booking.getCinemaId(), cinemaNameCache));
        response.setHallName(resolveHallName(booking, hallNameCache, showtimeCache));
        return response;
    }

    private String resolveCinemaName(UUID cinemaId, Map<UUID, String> cinemaNameCache) {
        if (cinemaId == null) {
            return null;
        }
        Map<UUID, String> cache = cinemaNameCache == null ? new LinkedHashMap<>() : cinemaNameCache;
        if (cache.containsKey(cinemaId)) {
            return cache.get(cinemaId);
        }
        try {
            String cinemaName = cinemaGrpcClient.getCinemaById(cinemaId).name();
            cache.put(cinemaId, cinemaName);
            return cinemaName;
        } catch (BusinessException ex) {
            cache.put(cinemaId, null);
            return null;
        }
    }

    private String resolveHallName(
            Booking booking,
            Map<UUID, String> hallNameCache,
            Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> showtimeCache) {
        if (booking == null || booking.getShowtimeId() == null) {
            return null;
        }

        ShowtimeGrpcClient.ShowtimeSummary showtime = null;
        boolean hasPrefetchedShowtime = showtimeCache != null && showtimeCache.containsKey(booking.getShowtimeId());
        if (hasPrefetchedShowtime) {
            showtime = showtimeCache.get(booking.getShowtimeId());
        }

        try {
            if (!hasPrefetchedShowtime && showtime == null) {
                showtime = showtimeGrpcClient.getShowtimeById(booking.getShowtimeId());
            }
            if (showtime == null || showtime.getHallId() == null) {
                return null;
            }

            UUID hallId = showtime.getHallId();
            Map<UUID, String> cache = hallNameCache == null ? new LinkedHashMap<>() : hallNameCache;
            if (cache.containsKey(hallId)) {
                return cache.get(hallId);
            }

            String hallName = hallGrpcClient.getHallById(hallId).name();
            cache.put(hallId, hallName);
            return hallName;
        } catch (BusinessException ex) {
            if (hallNameCache != null && showtime != null && showtime.getHallId() != null) {
                hallNameCache.put(showtime.getHallId(), null);
            }
            return null;
        }
    }

    private Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> prefetchShowtimeSummariesForBookingResponses(Collection<UUID> showtimeIds) {
        if (showtimeIds == null || showtimeIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> result = new LinkedHashMap<>();
        for (UUID showtimeId : showtimeIds) {
            if (showtimeId == null || result.containsKey(showtimeId)) {
                continue;
            }
            try {
                result.put(showtimeId, showtimeGrpcClient.getShowtimeById(showtimeId));
            } catch (BusinessException ex) {
                result.put(showtimeId, null);
            }
        }
        return result;
    }

    private List<FilterField<BookingField>> mergeForcedBookingFilters(
            List<FilterField<BookingField>> clientFilters,
            List<FilterField<BookingField>> forcedFilters) {
        if (forcedFilters == null || forcedFilters.isEmpty()) {
            return clientFilters;
        }

        List<FilterField<BookingField>> mergedFilters = new ArrayList<>();
        if (clientFilters != null) {
            for (FilterField<BookingField> filter : clientFilters) {
                if (filter == null || filter.getField() == null) {
                    continue;
                }
                if (filter.getField() == BookingField.BOOKING_STATUS
                        || filter.getField() == BookingField.PAYMENT_STATUS) {
                    continue;
                }
                mergedFilters.add(filter);
            }
        }
        mergedFilters.addAll(forcedFilters);
        return mergedFilters;
    }

    private List<FilterField<BookingField>> buildPurchasedBookingFilters() {
        return List.of(
                FilterField.<BookingField>builder()
                        .field(BookingField.BOOKING_STATUS)
                        .operator("EQ")
                        .value(BookingStatus.CONFIRMED)
                        .build(),
                FilterField.<BookingField>builder()
                        .field(BookingField.PAYMENT_STATUS)
                        .operator("EQ")
                        .value(PaymentStatus.PAID)
                        .build());
    }

    private List<FilterField<BookingField>> buildActiveBookingFilters(LocalDateTime now) {
        return List.of(
                FilterField.<BookingField>builder()
                        .field(BookingField.BOOKING_STATUS)
                        .operator("IN")
                        .value(List.of(BookingStatus.PENDING, BookingStatus.RESERVED))
                        .build(),
                FilterField.<BookingField>builder()
                        .field(BookingField.PAYMENT_STATUS)
                        .operator("EQ")
                        .value(PaymentStatus.UNPAID)
                        .build(),
                FilterField.<BookingField>builder()
                        .field(BookingField.RESERVED_UNTIL)
                        .operator("GTE")
                        .value(now)
                        .build());
    }

    private List<FilterField<BookingField>> buildUnpaidBookingFilters() {
        return List.of(
                FilterField.<BookingField>builder()
                        .field(BookingField.BOOKING_STATUS)
                        .operator("IN")
                        .value(List.of(
                                BookingStatus.PENDING,
                                BookingStatus.RESERVED,
                                BookingStatus.EXPIRED))
                        .build(),
                FilterField.<BookingField>builder()
                        .field(BookingField.PAYMENT_STATUS)
                        .operator("EQ")
                        .value(PaymentStatus.UNPAID)
                        .build());
    }

    private void validateRevenueReportRequest(BookingRevenueReportRequest request) {
        if (request == null || request.getPageRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        DateRange dateRange = request.getDateRange();
        if (dateRange != null
                && dateRange.getFrom() != null
                && dateRange.getTo() != null
                && dateRange.getFrom().isAfter(dateRange.getTo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateShowtimePerformanceReportRequest(ShowtimePerformanceReportRequest request) {
        if (request == null || request.getPageRequest() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        DateRange dateRange = request.getDateRange();
        if (dateRange != null
                && dateRange.getFrom() != null
                && dateRange.getTo() != null
                && dateRange.getFrom().isAfter(dateRange.getTo())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private BookingRevenueReportResponse buildBookingRevenueReport(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            BookingRevenueReportRequest request) {
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();
        PageRequest<BookingRevenueField> pageRequest = request.getPageRequest();
        List<BookingRevenueItemResponse> filteredItems = aggregateBookingRevenueItems(cinemas, request);
        int page = pageRequest.getPageOrDefault();
        int size = pageRequest.getSizeOrDefault();
        long totalElements = filteredItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<BookingRevenueItemResponse> pageItems = paginate(filteredItems, page, size);

        return BookingRevenueReportResponse.builder()
                .from(from)
                .to(to)
                .generatedAt(LocalDateTime.now())
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .items(pageItems)
                .page(toSummary(pageItems))
                .total(toSummary(filteredItems))
                .build();
    }

    private List<BookingRevenueItemResponse> aggregateBookingRevenueItems(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            BookingRevenueReportRequest request) {
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();
        List<UUID> requestedCinemaIds = normalizeUuidList(request.getCinemaIds());
        List<UUID> requestedFilmIds = normalizeUuidList(request.getFilmIds());

        List<CinemaGrpcClient.CinemaSummary> scopeCinemas = cinemas == null ? List.of() : new ArrayList<>(cinemas);
        scopeCinemas = scopeCinemas.stream()
                .filter(cinema -> cinema != null && cinema.id() != null)
                .filter(cinema -> requestedCinemaIds == null || requestedCinemaIds.contains(cinema.id()))
                .distinct()
                .toList();

        List<BookingRevenueItemResponse> allItems = initializeBookingRevenueItems(scopeCinemas);
        if (allItems.isEmpty()) {
            return List.of();
        }

        Map<UUID, BookingRevenueAccumulator> accumulatorMap = allItems.stream()
                .collect(java.util.stream.Collectors.toMap(
                        BookingRevenueItemResponse::cinemaId,
                        item -> new BookingRevenueAccumulator(item.cinemaId(), item.cinemaName()),
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<UUID> scopedCinemaIds = scopeCinemas.stream()
                .map(CinemaGrpcClient.CinemaSummary::id)
                .toList();
        List<Booking> bookings = requestedFilmIds == null
                ? bookingRepositoryImpl.findAllForBookingRevenueReport(
                        scopedCinemaIds,
                        from,
                        to,
                        EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED, BookingStatus.EXPIRED))
                : bookingRepositoryImpl.findAllForBookingRevenueReport(
                        scopedCinemaIds,
                        requestedFilmIds,
                        from,
                        to,
                        EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED, BookingStatus.EXPIRED));

        for (Booking booking : bookings) {
            BookingRevenueAccumulator accumulator = accumulatorMap.get(booking.getCinemaId());
            if (accumulator == null) {
                continue;
            }
            accumulator.addBooking(booking);
        }

        List<BookingRevenueItemResponse> allFilteredItems = accumulatorMap.values().stream()
                .map(BookingRevenueAccumulator::toResponse)
                .toList();
        return applyBookingRevenuePageRequest(allFilteredItems, request.getPageRequest());
    }

    private List<BookingRevenueItemResponse> filterSelectedBookingRevenueItems(
            List<BookingRevenueItemResponse> items,
            List<UUID> selectedIds) {
        List<UUID> normalizedSelectedIds = normalizeUuidList(selectedIds);
        if (normalizedSelectedIds == null) {
            return items == null ? List.of() : new ArrayList<>(items);
        }
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(item -> item != null
                        && item.cinemaId() != null
                        && normalizedSelectedIds.contains(item.cinemaId()))
                .toList();
    }

    private ShowtimePerformanceReportResponse buildShowtimePerformanceReport(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            ShowtimePerformanceReportRequest request) {
        DateRange dateRange = request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();
        PageRequest<ShowtimePerformanceField> pageRequest = request.getPageRequest();
        List<UUID> requestedCinemaIds = normalizeUuidList(request.getCinemaIds());
        List<UUID> requestedFilmIds = normalizeUuidList(request.getFilmIds());

        List<UUID> scopeCinemaIds = resolveScopeCinemaIds(cinemas, requestedCinemaIds);
        List<ShowtimePerformanceItemResponse> allItems = List.of();
        if (!scopeCinemaIds.isEmpty()) {
            List<Booking> bookings = bookingRepositoryImpl.findAllForShowtimePerformanceReport(
                    scopeCinemaIds,
                    requestedFilmIds,
                    from,
                    to,
                    EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED, BookingStatus.CONFIRMED));
            allItems = buildShowtimePerformanceItems(bookings);
        }

        List<ShowtimePerformanceItemResponse> filteredItems = applyShowtimePerformancePageRequest(allItems, pageRequest);
        int page = pageRequest.getPageOrDefault();
        int size = pageRequest.getSizeOrDefault();
        long totalElements = filteredItems.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        List<ShowtimePerformanceItemResponse> pageItems = paginateShowtimePerformance(filteredItems, page, size);

        return ShowtimePerformanceReportResponse.builder()
                .from(from)
                .to(to)
                .generatedAt(LocalDateTime.now())
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .items(pageItems)
                .page(toShowtimePerformanceSummary(pageItems))
                .total(toShowtimePerformanceSummary(filteredItems))
                .build();
    }

    private List<UUID> resolveScopeCinemaIds(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            List<UUID> requestedCinemaIds) {
        if (cinemas == null || cinemas.isEmpty()) {
            return List.of();
        }

        List<UUID> activeCinemaIds = cinemas.stream()
                .filter(cinema -> cinema != null && cinema.id() != null)
                .map(CinemaGrpcClient.CinemaSummary::id)
                .distinct()
                .toList();
        if (requestedCinemaIds == null) {
            return activeCinemaIds;
        }
        return activeCinemaIds.stream()
                .filter(requestedCinemaIds::contains)
                .distinct()
                .toList();
    }

    private List<ShowtimePerformanceItemResponse> buildShowtimePerformanceItems(List<Booking> bookings) {
        if (bookings == null || bookings.isEmpty()) {
            return List.of();
        }

        Map<UUID, Booking> firstBookingByShowtime = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            if (booking == null || booking.getShowtimeId() == null) {
                continue;
            }
            firstBookingByShowtime.putIfAbsent(booking.getShowtimeId(), booking);
        }

        if (firstBookingByShowtime.isEmpty()) {
            return List.of();
        }

        Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> showtimeMap = fetchShowtimeSummaries(firstBookingByShowtime.keySet());
        Map<UUID, Long> seatCapacityByHall = fetchSeatCapacitiesByHallId(showtimeMap.values());

        Map<UUID, ShowtimePerformanceAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (Booking booking : bookings) {
            if (booking == null || booking.getShowtimeId() == null) {
                continue;
            }
            ShowtimeGrpcClient.ShowtimeSummary showtime = showtimeMap.get(booking.getShowtimeId());
            if (showtime == null) {
                continue;
            }
            ShowtimePerformanceAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    booking.getShowtimeId(),
                    ignored -> ShowtimePerformanceAccumulator.fromBooking(booking, showtime));
            accumulator.addBooking(booking);
        }

        List<ShowtimePerformanceItemResponse> items = new ArrayList<>();
        for (ShowtimePerformanceAccumulator accumulator : accumulatorMap.values()) {
            Long capacity = seatCapacityByHall.get(accumulator.hallId());
            accumulator.setTotalSeatCapacity(capacity == null ? 0L : capacity);
            items.add(accumulator.toResponse());
        }
        return items;
    }

    private Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> fetchShowtimeSummaries(Collection<UUID> showtimeIds) {
        if (showtimeIds == null || showtimeIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ShowtimeGrpcClient.ShowtimeSummary> result = new LinkedHashMap<>();
        for (UUID showtimeId : showtimeIds) {
            if (showtimeId == null || result.containsKey(showtimeId)) {
                continue;
            }
            result.put(showtimeId, showtimeGrpcClient.getShowtimeById(showtimeId));
        }
        return result;
    }

    private Map<UUID, Long> fetchSeatCapacitiesByHallId(Collection<ShowtimeGrpcClient.ShowtimeSummary> showtimes) {
        if (showtimes == null || showtimes.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Long> result = new LinkedHashMap<>();
        for (ShowtimeGrpcClient.ShowtimeSummary showtime : showtimes) {
            if (showtime == null || showtime.getHallId() == null || result.containsKey(showtime.getHallId())) {
                continue;
            }
            SeatGrpcClient.LayoutBundle layout = seatGrpcClient.getLayoutByHallId(showtime.getHallId());
            long capacity = layout.getSeats() == null ? 0L : layout.getSeats().size();
            result.put(showtime.getHallId(), capacity);
        }
        return result;
    }

    private List<ShowtimePerformanceItemResponse> applyShowtimePerformancePageRequest(
            List<ShowtimePerformanceItemResponse> items,
            PageRequest<ShowtimePerformanceField> request) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<ShowtimePerformanceItemResponse> filtered = new ArrayList<>(items);
        String keyword = request.getNormalizedKeyword();
        if (StringUtils.hasText(keyword)) {
            filtered = filtered.stream()
                    .filter(item -> matchesShowtimePerformanceKeyword(item, keyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        List<FilterField<ShowtimePerformanceField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllShowtimePerformanceFilters(item, filters))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        filtered.sort(buildShowtimePerformanceComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<ShowtimePerformanceItemResponse> buildShowtimePerformanceComparator(
            List<SortField<ShowtimePerformanceField>> sortFields) {
        Comparator<ShowtimePerformanceItemResponse> defaultComparator = Comparator
                .comparing(ShowtimePerformanceItemResponse::startDateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ShowtimePerformanceItemResponse::showtimeId, Comparator.nullsLast(Comparator.naturalOrder()));

        if (sortFields == null || sortFields.isEmpty()) {
            return defaultComparator;
        }

        Comparator<ShowtimePerformanceItemResponse> merged = null;
        for (SortField<ShowtimePerformanceField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }
            Comparator<ShowtimePerformanceItemResponse> fieldComparator = (left, right) ->
                    compareValues(
                            getShowtimePerformanceFieldValue(left, sort.getField()),
                            getShowtimePerformanceFieldValue(right, sort.getField()));
            if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }

        return merged == null ? defaultComparator : merged.thenComparing(defaultComparator);
    }

    private boolean matchesShowtimePerformanceKeyword(ShowtimePerformanceItemResponse item, String keyword) {
        if (!StringUtils.hasText(keyword) || item == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(item.showtimeId() == null ? null : item.showtimeId().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.cinemaId() == null ? null : item.cinemaId().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.filmId() == null ? null : item.filmId().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.hallId() == null ? null : item.hallId().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.startDateTime() == null ? null : item.startDateTime().toString(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.endDateTime() == null ? null : item.endDateTime().toString(), keyword);
    }

    private boolean matchesAllShowtimePerformanceFilters(
            ShowtimePerformanceItemResponse item,
            List<FilterField<ShowtimePerformanceField>> filters) {
        for (FilterField<ShowtimePerformanceField> filter : filters) {
            if (!matchesShowtimePerformanceFilter(item, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesShowtimePerformanceFilter(
            ShowtimePerformanceItemResponse item,
            FilterField<ShowtimePerformanceField> filter) {
        if (item == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Object fieldValue = getShowtimePerformanceFieldValue(item, filter.getField());
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" -> compareValues(fieldValue, ShowtimePerformanceField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" -> compareValues(fieldValue, ShowtimePerformanceField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" -> compareValues(fieldValue, ShowtimePerformanceField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" -> compareValues(fieldValue, ShowtimePerformanceField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
            case "IN" -> matchesShowtimePerformanceInValues(fieldValue, rawValue, dataType);
            case "BETWEEN" -> matchesShowtimePerformanceBetweenValues(fieldValue, rawValue, dataType);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private boolean matchesShowtimePerformanceInValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertToShowtimePerformanceComparableList(rawValue, dataType);
        for (Comparable<?> value : values) {
            if (compareValues(fieldValue, value) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesShowtimePerformanceBetweenValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertToShowtimePerformanceComparableList(rawValue, dataType);
        if (values.size() != 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Comparable<?> start = values.get(0);
        Comparable<?> end = values.get(1);
        if (compareValues(start, end) > 0) {
            Comparable<?> tmp = start;
            start = end;
            end = tmp;
        }
        return compareValues(fieldValue, start) >= 0 && compareValues(fieldValue, end) <= 0;
    }

    private List<Comparable<?>> convertToShowtimePerformanceComparableList(Object rawValue, Class<?> dataType) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<Comparable<?>> values = new ArrayList<>();
        if (rawValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.add(ShowtimePerformanceField.convertValue(String.valueOf(item), dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(ShowtimePerformanceField.convertValue(String.valueOf(Array.get(rawValue, i)), dataType));
            }
            return values;
        }

        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        for (String token : text.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            values.add(ShowtimePerformanceField.convertValue(value, dataType));
        }
        return values;
    }

    private Object getShowtimePerformanceFieldValue(
            ShowtimePerformanceItemResponse item,
            ShowtimePerformanceField field) {
        return switch (field) {
            case SHOWTIME_ID -> item.showtimeId();
            case CINEMA_ID -> item.cinemaId();
            case FILM_ID -> item.filmId();
            case HALL_ID -> item.hallId();
            case START_DATE_TIME -> item.startDateTime();
            case END_DATE_TIME -> item.endDateTime();
            case TOTAL_BOOKINGS -> item.totalBookings();
            case TOTAL_SEATS_BOOKED -> item.totalSeatsBooked();
            case TOTAL_SEAT_CAPACITY -> item.totalSeatCapacity();
            case OCCUPANCY_RATE -> item.occupancyRate();
        };
    }

    private List<ShowtimePerformanceItemResponse> paginateShowtimePerformance(
            List<ShowtimePerformanceItemResponse> items,
            int page,
            int size) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, (page - 1) * size);
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(items.size(), fromIndex + size);
        return new ArrayList<>(items.subList(fromIndex, toIndex));
    }

    private ShowtimePerformanceSummaryResponse toShowtimePerformanceSummary(
            List<ShowtimePerformanceItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return ShowtimePerformanceSummaryResponse.builder()
                    .totalShowtimes(0)
                    .totalBookings(0)
                    .totalSeatsBooked(0)
                    .totalSeatCapacity(0)
                    .occupancyRate(BigDecimal.ZERO)
                    .build();
        }

        long totalShowtimes = 0L;
        long totalBookings = 0L;
        long totalSeatsBooked = 0L;
        long totalSeatCapacity = 0L;
        for (ShowtimePerformanceItemResponse item : items) {
            if (item == null) {
                continue;
            }
            totalShowtimes++;
            totalBookings += item.totalBookings();
            totalSeatsBooked += item.totalSeatsBooked();
            totalSeatCapacity += item.totalSeatCapacity();
        }

        return ShowtimePerformanceSummaryResponse.builder()
                .totalShowtimes(totalShowtimes)
                .totalBookings(totalBookings)
                .totalSeatsBooked(totalSeatsBooked)
                .totalSeatCapacity(totalSeatCapacity)
                .occupancyRate(calcOccupancyRate(totalSeatsBooked, totalSeatCapacity))
                .build();
    }

    private static BigDecimal calcOccupancyRate(long bookedSeats, long totalSeatCapacity) {
        if (totalSeatCapacity <= 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(bookedSeats)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalSeatCapacity), 2, RoundingMode.HALF_UP);
    }

    private static class ShowtimePerformanceAccumulator {
        private final UUID showtimeId;
        private final UUID cinemaId;
        private final UUID filmId;
        private final UUID hallId;
        private final LocalDateTime startDateTime;
        private final LocalDateTime endDateTime;
        private long totalBookings;
        private long totalSeatsBooked;
        private long totalSeatCapacity;

        private ShowtimePerformanceAccumulator(
                UUID showtimeId,
                UUID cinemaId,
                UUID filmId,
                UUID hallId,
                LocalDateTime startDateTime,
                LocalDateTime endDateTime) {
            this.showtimeId = showtimeId;
            this.cinemaId = cinemaId;
            this.filmId = filmId;
            this.hallId = hallId;
            this.startDateTime = startDateTime;
            this.endDateTime = endDateTime;
        }

        private static ShowtimePerformanceAccumulator fromBooking(
                Booking booking,
                ShowtimeGrpcClient.ShowtimeSummary showtime) {
            return new ShowtimePerformanceAccumulator(
                    booking.getShowtimeId(),
                    booking.getCinemaId(),
                    booking.getFilmId(),
                    showtime.getHallId(),
                    booking.getShowtimeStartDateTime() != null ? booking.getShowtimeStartDateTime() : showtime.getStartDateTime(),
                    booking.getShowtimeEndDateTime() != null ? booking.getShowtimeEndDateTime() : showtime.getEndDateTime());
        }

        private void addBooking(Booking booking) {
            if (booking == null) {
                return;
            }
            totalBookings++;
            totalSeatsBooked += booking.getSeatItems() == null ? 0 : booking.getSeatItems().size();
        }

        private UUID hallId() {
            return hallId;
        }

        private void setTotalSeatCapacity(long totalSeatCapacity) {
            this.totalSeatCapacity = totalSeatCapacity;
        }

        private ShowtimePerformanceItemResponse toResponse() {
            return ShowtimePerformanceItemResponse.builder()
                    .showtimeId(showtimeId)
                    .cinemaId(cinemaId)
                    .filmId(filmId)
                    .hallId(hallId)
                    .startDateTime(startDateTime)
                    .endDateTime(endDateTime)
                    .totalBookings(totalBookings)
                    .totalSeatsBooked(totalSeatsBooked)
                    .totalSeatCapacity(totalSeatCapacity)
                    .occupancyRate(calcOccupancyRate(totalSeatsBooked, totalSeatCapacity))
                    .build();
        }
    }

    private List<BookingRevenueItemResponse> initializeBookingRevenueItems(
            List<CinemaGrpcClient.CinemaSummary> cinemas) {
        if (cinemas == null || cinemas.isEmpty()) {
            return List.of();
        }
        return cinemas.stream()
                .map(cinema -> BookingRevenueItemResponse.builder()
                        .cinemaId(cinema.id())
                        .cinemaName(cinema.name())
                        .totalBookings(0)
                        .pendingCount(0)
                        .reservedCount(0)
                        .confirmedCount(0)
                        .expiredCount(0)
                        .conversionRate(BigDecimal.ZERO)
                        .build())
                .toList();
    }

    private List<BookingRevenueItemResponse> applyBookingRevenuePageRequest(
            List<BookingRevenueItemResponse> items,
            PageRequest<BookingRevenueField> request) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<BookingRevenueItemResponse> filtered = new ArrayList<>(items);
        String keyword = request.getNormalizedKeyword();
        if (StringUtils.hasText(keyword)) {
            filtered = filtered.stream()
                    .filter(item -> matchesKeyword(item, keyword))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        List<FilterField<BookingRevenueField>> filters = request.getFilterBy();
        if (filters != null && !filters.isEmpty()) {
            filtered = filtered.stream()
                    .filter(item -> matchesAllFilters(item, filters))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        filtered.sort(buildBookingRevenueComparator(request.getSortBy()));
        return filtered;
    }

    private Comparator<BookingRevenueItemResponse> buildBookingRevenueComparator(
            List<SortField<BookingRevenueField>> sortFields) {
        Comparator<BookingRevenueItemResponse> comparator = Comparator
                .comparing(BookingRevenueItemResponse::cinemaName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(BookingRevenueItemResponse::cinemaId, Comparator.nullsLast(Comparator.naturalOrder()));

        if (sortFields == null || sortFields.isEmpty()) {
            return comparator;
        }

        Comparator<BookingRevenueItemResponse> merged = null;
        for (SortField<BookingRevenueField> sort : sortFields) {
            if (sort == null || sort.getField() == null) {
                continue;
            }
            Comparator<BookingRevenueItemResponse> fieldComparator = (left, right) ->
                    compareValues(
                            getBookingRevenueFieldValue(left, sort.getField()),
                            getBookingRevenueFieldValue(right, sort.getField()));
            if ("DESC".equalsIgnoreCase(sort.getDirection())) {
                fieldComparator = fieldComparator.reversed();
            }
            merged = merged == null ? fieldComparator : merged.thenComparing(fieldComparator);
        }

        return merged == null ? comparator : comparator.thenComparing(merged);
    }

    private boolean matchesKeyword(BookingRevenueItemResponse item, String keyword) {
        if (!StringUtils.hasText(keyword) || item == null) {
            return true;
        }
        return SearchTextUtils.containsIgnoreCase(item.cinemaName(), keyword)
                || SearchTextUtils.containsIgnoreCase(item.cinemaId() == null ? null : item.cinemaId().toString(), keyword);
    }

    private boolean matchesAllFilters(
            BookingRevenueItemResponse item,
            List<FilterField<BookingRevenueField>> filters) {
        for (FilterField<BookingRevenueField> filter : filters) {
            if (!matchesFilter(item, filter)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFilter(BookingRevenueItemResponse item, FilterField<BookingRevenueField> filter) {
        if (item == null || filter == null || filter.getField() == null || filter.getOperator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        String operator = filter.getOperator().trim().toUpperCase(Locale.ROOT);
        Object rawValue = filter.getValue();
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Object fieldValue = getBookingRevenueFieldValue(item, filter.getField());
        Class<?> dataType = filter.getField().getDataType();

        return switch (operator) {
            case "EQ" -> compareValues(fieldValue, BookingRevenueField.convertValue(String.valueOf(rawValue), dataType)) == 0;
            case "NEQ" -> compareValues(fieldValue, BookingRevenueField.convertValue(String.valueOf(rawValue), dataType)) != 0;
            case "LIKE" -> fieldValue instanceof String text
                    && SearchTextUtils.containsIgnoreCase(text, String.valueOf(rawValue));
            case "GTE" -> compareValues(fieldValue, BookingRevenueField.convertValue(String.valueOf(rawValue), dataType)) >= 0;
            case "LTE" -> compareValues(fieldValue, BookingRevenueField.convertValue(String.valueOf(rawValue), dataType)) <= 0;
            case "IN" -> matchesInValues(fieldValue, rawValue, dataType);
            case "BETWEEN" -> matchesBetweenValues(fieldValue, rawValue, dataType);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private boolean matchesInValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertToComparableList(rawValue, dataType);
        for (Comparable<?> value : values) {
            if (compareValues(fieldValue, value) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesBetweenValues(Object fieldValue, Object rawValue, Class<?> dataType) {
        List<Comparable<?>> values = convertToComparableList(rawValue, dataType);
        if (values.size() != 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Comparable<?> start = values.get(0);
        Comparable<?> end = values.get(1);
        if (compareValues(start, end) > 0) {
            Comparable<?> tmp = start;
            start = end;
            end = tmp;
        }
        return compareValues(fieldValue, start) >= 0 && compareValues(fieldValue, end) <= 0;
    }

    private List<Comparable<?>> convertToComparableList(Object rawValue, Class<?> dataType) {
        if (rawValue == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<Comparable<?>> values = new ArrayList<>();
        if (rawValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                values.add(BookingRevenueField.convertValue(String.valueOf(item), dataType));
            }
            return values;
        }

        if (rawValue.getClass().isArray()) {
            int length = Array.getLength(rawValue);
            for (int i = 0; i < length; i++) {
                values.add(BookingRevenueField.convertValue(String.valueOf(Array.get(rawValue, i)), dataType));
            }
            return values;
        }

        String text = String.valueOf(rawValue).trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        for (String token : text.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            values.add(BookingRevenueField.convertValue(value, dataType));
        }
        return values;
    }

    private Object getBookingRevenueFieldValue(BookingRevenueItemResponse item, BookingRevenueField field) {
        return switch (field) {
            case CINEMA_ID -> item.cinemaId();
            case CINEMA_NAME -> item.cinemaName();
            case TOTAL_BOOKINGS -> item.totalBookings();
            case PENDING_COUNT -> item.pendingCount();
            case RESERVED_COUNT -> item.reservedCount();
            case CONFIRMED_COUNT -> item.confirmedCount();
            case EXPIRED_COUNT -> item.expiredCount();
            case CONVERSION_RATE -> item.conversionRate();
        };
    }

    private int compareValues(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Comparable<?> comparableLeft) {
            try {
                @SuppressWarnings("unchecked")
                Comparable<Object> typedLeft = (Comparable<Object>) comparableLeft;
                return typedLeft.compareTo(right);
            } catch (ClassCastException ex) {
                return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
            }
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
    }

    private List<BookingRevenueItemResponse> paginate(List<BookingRevenueItemResponse> items, int page, int size) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, (page - 1) * size);
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(items.size(), fromIndex + size);
        return new ArrayList<>(items.subList(fromIndex, toIndex));
    }

    private BookingRevenueSummaryResponse toSummary(List<BookingRevenueItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return BookingRevenueSummaryResponse.builder()
                    .totalBookings(0)
                    .pendingCount(0)
                    .reservedCount(0)
                    .confirmedCount(0)
                    .expiredCount(0)
                    .conversionRate(BigDecimal.ZERO)
                    .build();
        }

        long totalBookings = 0L;
        long pendingCount = 0L;
        long reservedCount = 0L;
        long confirmedCount = 0L;
        long expiredCount = 0L;

        for (BookingRevenueItemResponse item : items) {
            if (item == null) {
                continue;
            }
            totalBookings += item.totalBookings();
            pendingCount += item.pendingCount();
            reservedCount += item.reservedCount();
            confirmedCount += item.confirmedCount();
            expiredCount += item.expiredCount();
        }

        return BookingRevenueSummaryResponse.builder()
                .totalBookings(totalBookings)
                .pendingCount(pendingCount)
                .reservedCount(reservedCount)
                .confirmedCount(confirmedCount)
                .expiredCount(expiredCount)
                .conversionRate(calculateConversionRate(confirmedCount, totalBookings))
                .build();
    }

    private static BigDecimal calculateConversionRate(long confirmedCount, long totalBookings) {
        if (totalBookings <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(confirmedCount)
                .divide(BigDecimal.valueOf(totalBookings), 4, java.math.RoundingMode.HALF_UP);
    }

    private List<UUID> normalizeUuidList(Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<UUID> normalized = values.stream()
                .filter(value -> value != null)
                .distinct()
                .toList();
        return normalized.isEmpty() ? null : normalized;
    }

    private static class BookingRevenueAccumulator {
        private final UUID cinemaId;
        private final String cinemaName;
        private long totalBookings;
        private long pendingCount;
        private long reservedCount;
        private long confirmedCount;
        private long expiredCount;

        private BookingRevenueAccumulator(UUID cinemaId, String cinemaName) {
            this.cinemaId = cinemaId;
            this.cinemaName = cinemaName;
        }

        private void addBooking(Booking booking) {
            if (booking == null) {
                return;
            }
            totalBookings++;
            if (booking.getBookingStatus() == BookingStatus.PENDING) {
                pendingCount++;
            } else if (booking.getBookingStatus() == BookingStatus.RESERVED) {
                reservedCount++;
            } else if (booking.getBookingStatus() == BookingStatus.CONFIRMED) {
                confirmedCount++;
            } else if (booking.getBookingStatus() == BookingStatus.EXPIRED) {
                expiredCount++;
            }
        }

        private BookingRevenueItemResponse toResponse() {
            return BookingRevenueItemResponse.builder()
                    .cinemaId(cinemaId)
                    .cinemaName(cinemaName)
                    .totalBookings(totalBookings)
                    .pendingCount(pendingCount)
                    .reservedCount(reservedCount)
                    .confirmedCount(confirmedCount)
                    .expiredCount(expiredCount)
                    .conversionRate(calculateConversionRate(confirmedCount, totalBookings))
                    .build();
        }
    }

    private void authorizeBookingRead(Booking booking, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return;
        }
        if (HeaderNames.ROLE_CUSTOMER.equals(role)) {
            UUID userId = resolveUserId(httpRequest);
            if (!userId.equals(booking.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            if (booking.getBookingStatus() != BookingStatus.CONFIRMED
                    || booking.getPaymentStatus() != PaymentStatus.PAID) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        if (HeaderNames.ROLE_MANAGER.equals(role) || HeaderNames.ROLE_STAFF.equals(role)) {
            Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest, role);
            if (!accessibleCinemaIds.contains(booking.getCinemaId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void authorizeBookingCheckoutRead(Booking booking, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (!HeaderNames.ROLE_CUSTOMER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        UUID userId = resolveUserId(httpRequest);
        if (!userId.equals(booking.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        boolean activeStatus = booking.getBookingStatus() == BookingStatus.PENDING
                || booking.getBookingStatus() == BookingStatus.RESERVED;
        boolean notExpired = booking.getReservedUntil() != null && booking.getReservedUntil().isAfter(LocalDateTime.now());
        boolean unpaid = booking.getPaymentStatus() != PaymentStatus.PAID;

        if (!activeStatus || !notExpired || !unpaid) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private List<BookingSeatItem> buildSeatItems(
            Booking booking,
            List<CreateBookingRequest.SeatItem> seatItems,
            List<String> normalizedSeatCodes,
            Map<String, SeatGrpcClient.SeatSnapshot> canonicalSeatSnapshotByCode) {
        List<BookingSeatItem> result = new ArrayList<>(seatItems.size());
        for (int i = 0; i < seatItems.size(); i++) {
            CreateBookingRequest.SeatItem seatItemRequest = seatItems.get(i);
            BookingSeatItem seatItem = bookingSeatItemMapper.toEntity(seatItemRequest);
            seatItem.setBooking(booking);
            String seatCode = normalizedSeatCodes.get(i);
            SeatGrpcClient.SeatSnapshot seatSnapshot = canonicalSeatSnapshotByCode.get(seatCode);
            if (seatSnapshot == null) {
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
            }
            seatItem.setSeatId(seatSnapshot.seatId());
            seatItem.setSeatCode(seatCode);
            seatItem.setSeatType(seatSnapshot.seatType());
            result.add(seatItem);
        }
        return result;
    }

    private List<BookingProductItem> buildProductItems(
            Booking booking,
            List<CreateBookingRequest.ProductItem> productItems,
            UUID cinemaId) {
        if (productItems == null || productItems.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> mergedQuantities = new LinkedHashMap<>();
        for (CreateBookingRequest.ProductItem item : productItems) {
            mergedQuantities.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        Set<UUID> productIds = mergedQuantities.keySet();
        List<Product> products = productRepository.findAllByIdInAndIsDeletedFalse(productIds);
        if (products.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        Map<UUID, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<BookingProductItem> result = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : mergedQuantities.entrySet()) {
            Product product = productMap.get(entry.getKey());
            if (!cinemaId.equals(product.getCinemaId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }

            Integer quantity = entry.getValue();
            BigDecimal unitPrice = product.getPrice();
            BookingProductItem item = new BookingProductItem();
            item.setBooking(booking);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setProductNameSnapshot(product.getName());
            item.setUnitPriceSnapshot(unitPrice);
            item.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            result.add(item);
        }

        return result;
    }

    private List<String> normalizeAndValidateSeatCodes(List<CreateBookingRequest.SeatItem> seatItems) {
        List<String> normalized = new ArrayList<>(seatItems.size());
        Set<String> unique = new LinkedHashSet<>();
        for (CreateBookingRequest.SeatItem seatItem : seatItems) {
            String seatCode = normalizeSeatCode(seatItem.getSeatCode());
            if (!unique.add(seatCode)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            normalized.add(seatCode);
        }
        return normalized;
    }

    private String normalizeSeatCode(String seatCode) {
        return seatCode == null ? "" : seatCode.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, SeatGrpcClient.SeatSnapshot> validateAndResolveSeatSnapshots(
            UUID hallId,
            List<CreateBookingRequest.SeatItem> seatItems,
            List<String> normalizedSeatCodes) {
        Map<String, SeatGrpcClient.SeatSnapshot> canonicalSeatSnapshotByCode =
                seatGrpcClient.getSeatSnapshotsByCodes(hallId, normalizedSeatCodes);
        if (canonicalSeatSnapshotByCode.size() != normalizedSeatCodes.size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }

        for (int i = 0; i < seatItems.size(); i++) {
            String seatCode = normalizedSeatCodes.get(i);
            SeatGrpcClient.SeatSnapshot snapshot = canonicalSeatSnapshotByCode.get(seatCode);
            HallEnum.SeatType canonicalType = snapshot == null ? null : snapshot.seatType();
            if (canonicalType == null || canonicalType == HallEnum.SeatType.AISLE) {
                throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
            }
            if (seatItems.get(i).getSeatType() != canonicalType) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
        }
        return canonicalSeatSnapshotByCode;
    }

    private boolean isTerminalStatus(BookingStatus status) {
        return status == BookingStatus.CANCELLED
                || status == BookingStatus.EXPIRED
                || status == BookingStatus.CONFIRMED;
    }

    private boolean isPayableSessionStatus(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return !"PAID".equals(normalized) && !"REFUNDED".equals(normalized);
    }

    private List<String> extractSeatCodes(List<BookingSeatItem> seatItems) {
        if (seatItems == null || seatItems.isEmpty()) {
            return List.of();
        }
        return seatItems.stream()
                .map(BookingSeatItem::getSeatCode)
                .map(this::normalizeSeatCode)
                .toList();
    }

    private void validateBookingCreatorRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(
                httpRequest,
                HeaderNames.ROLE_CUSTOMER,
                HeaderNames.ROLE_ADMIN,
                HeaderNames.ROLE_MANAGER,
                HeaderNames.ROLE_STAFF);
    }

    private void validateCustomerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_CUSTOMER);
    }

    private void validateOperatorRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
    }

    private UUID resolveUserId(HttpServletRequest httpRequest) {
        return RequestAuthUtils.requireUserId(httpRequest);
    }

    private Set<UUID> resolveAccessibleCinemaIdsByUser(HttpServletRequest httpRequest, String role) {
        try {
            UUID userId = resolveUserId(httpRequest);
            List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
            if (cinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            return new java.util.HashSet<>(cinemaIds);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.CINEMA_NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            throw ex;
        }
    }
}
