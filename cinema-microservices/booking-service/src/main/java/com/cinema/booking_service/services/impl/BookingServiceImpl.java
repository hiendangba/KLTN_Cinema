package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.dto.request.CreateBookingRequest;
import com.cinema.booking_service.dto.request.UpdateBookingStatusRequest;
import com.cinema.booking_service.dto.response.BookingResponse;
import com.cinema.booking_service.entity.Booking;
import com.cinema.booking_service.entity.BookingProductItem;
import com.cinema.booking_service.entity.BookingSeatItem;
import com.cinema.booking_service.entity.Product;
import com.cinema.booking_service.enums.BookingStatus;
import com.cinema.booking_service.enums.PaymentStatus;
import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.grpc.CinemaGrpcClient;
import com.cinema.booking_service.mapper.BookingMapper;
import com.cinema.booking_service.mapper.BookingSeatItemMapper;
import com.cinema.booking_service.repository.BookingRepository;
import com.cinema.booking_service.repository.BookingSeatItemRepository;
import com.cinema.booking_service.repository.ProductRepository;
import com.cinema.booking_service.services.BookingService;
import com.cinema.booking_service.services.SeatLockService;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final BookingSeatItemRepository bookingSeatItemRepository;
    private final ProductRepository productRepository;
    private final BookingMapper bookingMapper;
    private final BookingSeatItemMapper bookingSeatItemMapper;
    private final CinemaGrpcClient cinemaGrpcClient;
    private final SeatLockService seatLockService;

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
        booking.setBookingStatus(BookingStatus.RESERVED);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setReservedUntil(LocalDateTime.now().plusMinutes(seatLockMinutes));

        List<BookingSeatItem> persistedSeatItems = buildSeatItems(booking, seatItems, normalizedSeatCodes);
        booking.setSeatItems(persistedSeatItems);
        BigDecimal ticketSubtotal = persistedSeatItems.stream()
                .map(BookingSeatItem::getSeatPriceSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        booking.setTicketSubtotal(ticketSubtotal);

        List<BookingProductItem> persistedProductItems = buildProductItems(booking, request.getProductItems(), request.getCinemaId());
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
    public List<BookingResponse> getBookingsByOperatorCinema(HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        return bookingRepository.findAllByCinemaIdAndIsDeletedFalseOrderByTimeCreatedDesc(cinemaId)
                .stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateBookingStatus(UUID id, UpdateBookingStatusRequest request, HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);

        Booking booking = getActiveBookingOrThrow(id);
        if (!cinemaId.equals(booking.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
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
        String role = resolveRole(httpRequest);
        if (HeaderNames.ROLE_CUSTOMER.equals(role)) {
            UUID userId = resolveUserId(httpRequest);
            if (!userId.equals(booking.getUserId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }

        if (HeaderNames.ROLE_MANAGER.equals(role) || HeaderNames.ROLE_STAFF.equals(role)) {
            UUID cinemaId = resolveCinemaIdByUser(httpRequest);
            if (!cinemaId.equals(booking.getCinemaId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }
    }

    private List<BookingSeatItem> buildSeatItems(
            Booking booking,
            List<CreateBookingRequest.SeatItem> seatItems,
            List<String> normalizedSeatCodes) {
        List<BookingSeatItem> result = new ArrayList<>(seatItems.size());
        for (int i = 0; i < seatItems.size(); i++) {
            CreateBookingRequest.SeatItem seatItemRequest = seatItems.get(i);
            BookingSeatItem seatItem = bookingSeatItemMapper.toEntity(seatItemRequest);
            seatItem.setBooking(booking);
            seatItem.setSeatCode(normalizedSeatCodes.get(i));
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

    private boolean isTerminalStatus(BookingStatus status) {
        return status == BookingStatus.CANCELLED
                || status == BookingStatus.EXPIRED
                || status == BookingStatus.CONFIRMED;
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
        String role = resolveRole(httpRequest);
        if (!HeaderNames.ROLE_CUSTOMER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateOperatorRole(HttpServletRequest httpRequest) {
        String role = resolveRole(httpRequest);
        if (!(HeaderNames.ROLE_MANAGER.equals(role) || HeaderNames.ROLE_STAFF.equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private String resolveRole(HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (role == null || role.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return role;
    }

    private UUID resolveUserId(HttpServletRequest httpRequest) {
        String userIdRaw = httpRequest.getHeader(HeaderNames.X_USER_ID);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
    }

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        UUID userId = resolveUserId(httpRequest);
        try {
            return cinemaGrpcClient.getCinemaIdByUserId(userId);
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
