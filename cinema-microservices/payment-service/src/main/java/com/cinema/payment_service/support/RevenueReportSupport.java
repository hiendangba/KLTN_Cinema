package com.cinema.payment_service.support;

import com.cinema.dto.request.DateRange;
import com.cinema.payment_service.dto.request.CinemaRevenueReportRequest;
import com.cinema.payment_service.dto.request.FilmRevenueReportRequest;
import com.cinema.payment_service.dto.response.CinemaRevenueItemResponse;
import com.cinema.payment_service.dto.response.FilmRevenueItemResponse;
import com.cinema.payment_service.entity.PaymentTransaction;
import com.cinema.payment_service.entity.PaymentTransactionPromotion;
import com.cinema.payment_service.grpc.CinemaGrpcClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RevenueReportSupport {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);

    public List<CinemaRevenueItemResponse> aggregateCinemaRevenueItems(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            CinemaRevenueReportRequest request,
            List<PaymentTransaction> revenueTransactions,
            List<PaymentTransactionPromotion> promotionSnapshots) {
        List<CinemaGrpcClient.CinemaSummary> scopeCinemas = filterCinemas(cinemas, request);
        if (scopeCinemas.isEmpty()) {
            return List.of();
        }
        LocalDateTime from = resolveFrom(request);
        LocalDateTime to = resolveTo(request);

        Map<UUID, CinemaRevenueAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (CinemaGrpcClient.CinemaSummary cinema : scopeCinemas) {
            accumulatorMap.put(cinema.id(), new CinemaRevenueAccumulator(cinema.id(), cinema.name()));
        }

        Map<UUID, List<PromotionSnapshotView>> promotionSnapshotsByTransactionId = groupPromotionSnapshots(
                promotionSnapshots);
        for (PaymentTransaction transaction : safeTransactions(revenueTransactions)) {
            if (transaction == null) {
                continue;
            }
            CinemaRevenueAccumulator accumulator = accumulatorMap.get(transaction.getCinemaId());
            if (accumulator == null) {
                continue;
            }
            applyTransactionToAccumulator(
                    accumulator,
                    transaction,
                    promotionSnapshotsByTransactionId.get(transaction.getId()),
                    from,
                    to);
        }

        return accumulatorMap.values().stream()
                .map(CinemaRevenueAccumulator::toResponse)
                .toList();
    }

    public List<FilmRevenueItemResponse> aggregateFilmRevenueItems(
            List<PaymentTransaction> revenueTransactions,
            Map<UUID, String> filmNames,
            FilmRevenueReportRequest request) {
        DateRange dateRange = request == null ? null : request.getDateRange();
        LocalDateTime from = dateRange == null ? null : dateRange.getFrom();
        LocalDateTime to = dateRange == null ? null : dateRange.getTo();

        if (revenueTransactions == null || revenueTransactions.isEmpty()) {
            return List.of();
        }

        Map<UUID, FilmRevenueAccumulator> accumulatorMap = new LinkedHashMap<>();
        for (PaymentTransaction transaction : revenueTransactions) {
            if (transaction == null || transaction.getFilmId() == null) {
                continue;
            }
            FilmRevenueAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    transaction.getFilmId(),
                    filmId -> new FilmRevenueAccumulator(filmId, normalizeStringValue(
                            filmNames == null ? null : filmNames.get(filmId))));
            accumulator.apply(transaction, from, to);
        }

        return accumulatorMap.values().stream()
                .map(FilmRevenueAccumulator::toResponse)
                .toList();
    }

    private List<CinemaGrpcClient.CinemaSummary> filterCinemas(
            List<CinemaGrpcClient.CinemaSummary> cinemas,
            CinemaRevenueReportRequest request) {
        List<CinemaGrpcClient.CinemaSummary> normalizedCinemas = new ArrayList<>();
        if (cinemas != null) {
            Map<UUID, CinemaGrpcClient.CinemaSummary> uniqueCinemas = new LinkedHashMap<>();
            for (CinemaGrpcClient.CinemaSummary cinema : cinemas) {
                if (cinema == null || cinema.id() == null || uniqueCinemas.containsKey(cinema.id())) {
                    continue;
                }
                uniqueCinemas.put(cinema.id(), cinema);
            }
            normalizedCinemas.addAll(uniqueCinemas.values());
        }

        List<UUID> requestedCinemaIds = request == null ? null : normalizeUuidList(request.getCinemaIds());
        if (requestedCinemaIds == null) {
            return normalizedCinemas;
        }
        return normalizedCinemas.stream()
                .filter(cinema -> requestedCinemaIds.contains(cinema.id()))
                .toList();
    }

    private Map<UUID, List<PromotionSnapshotView>> groupPromotionSnapshots(
            List<PaymentTransactionPromotion> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<PromotionSnapshotView>> grouped = new LinkedHashMap<>();
        for (PaymentTransactionPromotion snapshot : snapshots) {
            if (snapshot == null || snapshot.getPaymentTransactionId() == null) {
                continue;
            }
            grouped.computeIfAbsent(snapshot.getPaymentTransactionId(), key -> new ArrayList<>())
                    .add(new PromotionSnapshotView(
                            snapshot.getPromotionId(),
                            normalizeStringValue(snapshot.getPromotionCode()),
                            normalizeStringValue(snapshot.getPromotionName()),
                            normalizeAmount(snapshot.getDiscountAmount()),
                            snapshot.getApplyOrder(),
                            snapshot.getTimeCreated()));
        }
        return grouped;
    }

    private void applyTransactionToAccumulator(
            CinemaRevenueAccumulator accumulator,
            PaymentTransaction transaction,
            List<PromotionSnapshotView> promotionSnapshots,
            LocalDateTime from,
            LocalDateTime to) {
        if (transaction == null) {
            return;
        }

        if (isBetween(transaction.getPaidAt(), from, to)) {
            BigDecimal amount = normalizeAmount(transaction.getAmount());
            List<PromotionSnapshotView> resolvedPromotionSnapshots = resolvePromotionSnapshots(
                    transaction,
                    promotionSnapshots);
            accumulator.totalTransactions++;
            accumulator.paidCount++;
            accumulator.paidAmount = accumulator.paidAmount.add(amount);
            accumulator.appendPromotionSnapshots(resolvedPromotionSnapshots);
            accumulator.promotionDiscountAmount = accumulator.promotionDiscountAmount.add(
                    sumPromotionDiscountSnapshots(resolvedPromotionSnapshots));
            accumulator.loyaltyPointsDiscountAmount = accumulator.loyaltyPointsDiscountAmount.add(
                    normalizeAmount(BigDecimal.valueOf(
                            transaction.getLoyaltyPointsUsed() == null ? 0L : transaction.getLoyaltyPointsUsed())));
            accumulator.grossAmount = accumulator.grossAmount.add(amount);
            accumulator.netAmount = accumulator.netAmount.add(amount);
            accumulator.ticketSubtotalAmount = accumulator.ticketSubtotalAmount.add(
                    normalizeAmount(transaction.getTicketSubtotalSnapshot()));
            accumulator.productSubtotalAmount = accumulator.productSubtotalAmount.add(
                    normalizeAmount(transaction.getProductSubtotalSnapshot()));
        }

        if (isBetween(transaction.getRefundedAt(), from, to)) {
            BigDecimal refundAmount = transaction.getRefundAmount() != null
                    ? normalizeAmount(transaction.getRefundAmount())
                    : normalizeAmount(transaction.getAmount());
            accumulator.totalTransactions++;
            accumulator.refundedCount++;
            accumulator.refundedAmount = accumulator.refundedAmount.add(refundAmount);
            accumulator.netAmount = accumulator.netAmount.subtract(refundAmount);
            accumulator.ticketSubtotalAmount = accumulator.ticketSubtotalAmount.subtract(
                    normalizeAmount(transaction.getTicketSubtotalSnapshot()));
            accumulator.productSubtotalAmount = accumulator.productSubtotalAmount.subtract(
                    normalizeAmount(transaction.getProductSubtotalSnapshot()));
        }
    }

    private BigDecimal sumPromotionDiscountSnapshots(List<PromotionSnapshotView> promotionSnapshots) {
        if (promotionSnapshots == null || promotionSnapshots.isEmpty()) {
            return ZERO;
        }
        BigDecimal total = ZERO;
        for (PromotionSnapshotView snapshot : promotionSnapshots) {
            if (snapshot == null) {
                continue;
            }
            total = total.add(normalizeAmount(snapshot.discountAmount()));
        }
        return normalizeAmount(total);
    }

    private List<PromotionSnapshotView> resolvePromotionSnapshots(
            PaymentTransaction transaction,
            List<PromotionSnapshotView> promotionSnapshots) {
        if (promotionSnapshots != null && !promotionSnapshots.isEmpty()) {
            return promotionSnapshots;
        }

        if (transaction == null || !StringUtils.hasText(transaction.getPromotionCode())) {
            return List.of();
        }

        return List.of(new PromotionSnapshotView(
                null,
                normalizeStringValue(transaction.getPromotionCode()),
                normalizeStringValue(transaction.getPromotionName()),
                normalizeAmount(transaction.getPromotionDiscountAmount()),
                1,
                transaction.getTimeCreated()));
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

    private List<PaymentTransaction> safeTransactions(List<PaymentTransaction> transactions) {
        return transactions == null ? List.of() : transactions;
    }

    private LocalDateTime resolveFrom(CinemaRevenueReportRequest request) {
        DateRange dateRange = request == null ? null : request.getDateRange();
        return dateRange == null ? null : dateRange.getFrom();
    }

    private LocalDateTime resolveTo(CinemaRevenueReportRequest request) {
        DateRange dateRange = request == null ? null : request.getDateRange();
        return dateRange == null ? null : dateRange.getTo();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    private String normalizeStringValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isBetween(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value == null || from == null || to == null) {
            if (value == null) {
                return false;
            }
            if (from == null && to == null) {
                return true;
            }
            if (from == null) {
                return !value.isAfter(to);
            }
            if (to == null) {
                return !value.isBefore(from);
            }
            return false;
        }
        return !value.isBefore(from) && !value.isAfter(to);
    }

    private static class CinemaRevenueAccumulator {
        private final UUID cinemaId;
        private final String cinemaName;
        private long totalTransactions;
        private long pendingCount;
        private long paidCount;
        private long failedCount;
        private long expiredCount;
        private long refundPendingCount;
        private long refundedCount;
        private BigDecimal ticketSubtotalAmount = ZERO;
        private BigDecimal productSubtotalAmount = ZERO;
        private final List<String> promotionCodes = new ArrayList<>();
        private final List<String> promotionNames = new ArrayList<>();
        private BigDecimal promotionDiscountAmount = ZERO;
        private BigDecimal loyaltyPointsDiscountAmount = ZERO;
        private BigDecimal paidAmount = ZERO;
        private BigDecimal refundedAmount = ZERO;
        private BigDecimal grossAmount = ZERO;
        private BigDecimal netAmount = ZERO;

        private CinemaRevenueAccumulator(UUID cinemaId, String cinemaName) {
            this.cinemaId = cinemaId;
            this.cinemaName = cinemaName;
        }

        private CinemaRevenueItemResponse toResponse() {
            return CinemaRevenueItemResponse.builder()
                    .cinemaId(cinemaId)
                    .cinemaName(cinemaName)
                    .totalTransactions(totalTransactions)
                    .pendingCount(pendingCount)
                    .paidCount(paidCount)
                    .failedCount(failedCount)
                    .expiredCount(expiredCount)
                    .refundPendingCount(refundPendingCount)
                    .refundedCount(refundedCount)
                    .ticketSubtotalAmount(ticketSubtotalAmount)
                    .productSubtotalAmount(productSubtotalAmount)
                    .promotionCode(joinPromotionTokens(promotionCodes))
                    .promotionName(joinPromotionTokens(promotionNames))
                    .promotionDiscountAmount(promotionDiscountAmount)
                    .loyaltyPointsDiscountAmount(loyaltyPointsDiscountAmount)
                    .paidAmount(paidAmount)
                    .refundedAmount(refundedAmount)
                    .grossAmount(grossAmount)
                    .netAmount(netAmount)
                    .build();
        }

        private void appendPromotionSnapshots(List<PromotionSnapshotView> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return;
            }
            for (PromotionSnapshotView snapshot : snapshots) {
                if (snapshot == null) {
                    continue;
                }
                appendPromotionTokens(promotionCodes, snapshot.promotionCode());
                appendPromotionTokens(promotionNames, snapshot.promotionName());
            }
        }
    }

    private class FilmRevenueAccumulator {
        private final UUID filmId;
        private final String filmName;
        private final LinkedHashSet<UUID> cinemaIds = new LinkedHashSet<>();
        private long totalTransactions;
        private long paidCount;
        private long refundedCount;
        private BigDecimal paidAmount = ZERO;

        private FilmRevenueAccumulator(UUID filmId, String filmName) {
            this.filmId = filmId;
            this.filmName = filmName;
        }

        private void apply(PaymentTransaction transaction, LocalDateTime from, LocalDateTime to) {
            if (transaction == null) {
                return;
            }

            if (isBetween(transaction.getPaidAt(), from, to)) {
                totalTransactions++;
                paidCount++;
                paidAmount = paidAmount.add(normalizeAmount(transaction.getAmount()));
                if (transaction.getCinemaId() != null) {
                    cinemaIds.add(transaction.getCinemaId());
                }
            }

            if (isBetween(transaction.getRefundedAt(), from, to)) {
                totalTransactions++;
                refundedCount++;
                if (transaction.getCinemaId() != null) {
                    cinemaIds.add(transaction.getCinemaId());
                }
            }
        }

        private FilmRevenueItemResponse toResponse() {
            return FilmRevenueItemResponse.builder()
                    .filmId(filmId)
                    .filmName(filmName)
                    .cinemaCount(cinemaIds.size())
                    .totalTransactions(totalTransactions)
                    .paidCount(paidCount)
                    .refundedCount(refundedCount)
                    .paidAmount(paidAmount)
                    .build();
        }
    }

    private record PromotionSnapshotView(
            UUID promotionId,
            String promotionCode,
            String promotionName,
            BigDecimal discountAmount,
            Integer applyOrder,
            LocalDateTime timeCreated) {
    }

    private static void appendPromotionTokens(List<String> target, String value) {
        if (target == null || !StringUtils.hasText(value)) {
            return;
        }
        target.add(value.trim());
    }

    private static String joinPromotionTokens(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
