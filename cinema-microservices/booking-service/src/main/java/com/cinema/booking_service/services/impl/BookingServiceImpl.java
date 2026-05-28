package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.BookingField;
import com.cinema.booking_service.dto.request.UpdateBookingStatusRequest;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.dto.response.CheckoutContextResponse;
import com.cinema.booking_service.dto.response.PaymentSessionSnapshotResponse;
import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.entity.BookingProductItem;
import com.cinema.booking_service.entity.BookingSeatItem;
import com.cinema.booking_service.entity.Product;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.grpc.CinemaGrpcClient;
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
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.request.SortField;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingRepositoryImpl bookingRepositoryImpl;
    private final BookingSeatItemRepository bookingSeatItemRepository;
    private final ProductRepository productRepository;
    private final BookingMapper bookingMapper;
    private final BookingSeatItemMapper bookingSeatItemMapper;
    private final CinemaGrpcClient cinemaGrpcClient;
    private final ShowtimeGrpcClient showtimeGrpcClient;
    private final SeatGrpcClient seatGrpcClient;
    private final SeatLockService seatLockService;
    private final PaymentServiceClient paymentServiceClient;

    @Value("${booking.seat-lock-minutes:5}")
    private long seatLockMinutes;

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);
        UUID userId = resolveUserId(httpRequest);

        List<CreateBookingRequest.SeatItem> seatItems = request.getSeatItems();
        if (seatItems == null || seatItems.isEmpty() || seatItems.size() > 5) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        List<String> normalizedSeatCodes = normalizeAndValidateSeatCodes(seatItems);
        ShowtimeGrpcClient.ShowtimeSummary showtime = showtimeGrpcClient.getShowtimeById(request.getShowtimeId());
        if (!showtime.getCinemaId().equals(request.getCinemaId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
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

        Duration ttl = Duration.ofMinutes(seatLockMinutes);
        boolean locked = seatLockService.tryLockSeats(request.getShowtimeId(), normalizedSeatCodes, booking.getId(), ttl);
        if (!locked) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        try {
            Booking saved = bookingRepository.save(booking);
            return bookingMapper.toResponse(saved);
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
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);
        UUID userId = resolveUserId(httpRequest);
        return bookingRepository.findAllByUserIdAndIsDeletedFalseOrderByTimeCreatedDesc(userId)
                .stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> searchMyBookings(
            PageRequest<BookingField> request,
            HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);
        UUID userId = resolveUserId(httpRequest);

        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        String keyword = request.getNormalizedKeyword();

        List<SortField<BookingField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        if (sortFields.stream().noneMatch(sort -> sort != null && sort.getField() == BookingField.TIME_CREATED)) {
            sortFields.add(new SortField<>(BookingField.TIME_CREATED, "DESC"));
        }

        long totalElements = bookingRepositoryImpl.countWithFilter(userId, keyword, request.getFilterBy());
        List<Booking> bookings = bookingRepositoryImpl.searchWithPageAndSortAndFilter(
                userId, keyword, page, size, sortFields, request.getFilterBy());
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        return PageResponse.<BookingResponse>builder()
                .data(bookings.stream().map(bookingMapper::toResponse).toList())
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(totalElements)
                .size(size)
                .hasNext(page < totalPages)
                .hasPrevious(page > 1)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getMyActiveBooking(UUID showtimeId, UUID cinemaId, HttpServletRequest httpRequest) {
        validateCustomerRole(httpRequest);
        UUID userId = resolveUserId(httpRequest);
        List<Booking> bookings = bookingRepository.findActiveBookingsByUser(
                userId,
                EnumSet.of(BookingStatus.PENDING, BookingStatus.RESERVED),
                LocalDateTime.now(),
                showtimeId,
                cinemaId,
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (bookings.isEmpty()) {
            return null;
        }
        return bookingMapper.toResponse(bookings.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByOperatorCinema(HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return bookingRepository.findAllByIsDeletedFalseOrderByTimeCreatedDesc()
                    .stream()
                    .map(bookingMapper::toResponse)
                    .toList();
        }

        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest, role);
        return bookingRepository.findAllByCinemaIdInAndIsDeletedFalseOrderByTimeCreatedDesc(accessibleCinemaIds)
                .stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutContextResponse getCheckoutContext(UUID id, HttpServletRequest httpRequest) {
        Booking booking = getActiveBookingOrThrow(id);
        authorizeBookingRead(booking, httpRequest);

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
                .booking(bookingMapper.toResponse(booking))
                .paymentSession(paymentSession)
                .secondsToExpire(secondsToExpire)
                .canPay(activeStatus && notExpired && unpaid && paymentSessionAllowsPay)
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateBookingStatus(UUID id, UpdateBookingStatusRequest request, HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);

        Booking booking = getActiveBookingOrThrow(id);
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (!HeaderNames.ROLE_ADMIN.equals(role)) {
            Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest, role);
            if (!accessibleCinemaIds.contains(booking.getCinemaId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        if (request.getBookingStatus() == BookingStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        booking.setBookingStatus(request.getBookingStatus());
        if (request.getPaymentStatus() != null) {
            booking.setPaymentStatus(request.getPaymentStatus());
        }
        bookingRepository.save(booking);

        if (isTerminalStatus(booking.getBookingStatus())) {
            seatLockService.releaseSeats(booking.getShowtimeId(), extractSeatCodes(booking.getSeatItems()));
        }

        return ActionMessageResponse.builder()
                .message("Booking status updated successfully")
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
                .message("Booking cancelled successfully")
                .build();
    }

    private Booking getActiveBookingOrThrow(UUID id) {
        return bookingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
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


