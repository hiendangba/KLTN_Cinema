package com.cinema.payment_service.support;

import com.cinema.dto.request.DateRange;
import com.cinema.payment_service.dto.request.FilmRevenueReportRequest;
import com.cinema.payment_service.dto.response.FilmRevenueItemResponse;
import com.cinema.payment_service.entity.PaymentTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RevenueReportSupportTest {

    private final RevenueReportSupport revenueReportSupport = new RevenueReportSupport();

    @Test
    void aggregateFilmRevenueItems_shouldGroupByFilmAndCountDistinctCinemas() {
        UUID filmA = UUID.randomUUID();
        UUID filmB = UUID.randomUUID();
        UUID cinema1 = UUID.randomUUID();
        UUID cinema2 = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 12, 0);

        FilmRevenueReportRequest request = new FilmRevenueReportRequest();
        DateRange dateRange = new DateRange();
        dateRange.setFrom(now.minusDays(1));
        dateRange.setTo(now.plusDays(1));
        request.setDateRange(dateRange);

        List<PaymentTransaction> transactions = new ArrayList<>();

        PaymentTransaction paidA1 = new PaymentTransaction();
        paidA1.setFilmId(filmA);
        paidA1.setCinemaId(cinema1);
        paidA1.setPaidAt(now);
        paidA1.setAmount(BigDecimal.valueOf(100000));
        transactions.add(paidA1);

        PaymentTransaction paidA2 = new PaymentTransaction();
        paidA2.setFilmId(filmA);
        paidA2.setCinemaId(cinema2);
        paidA2.setPaidAt(now);
        paidA2.setAmount(BigDecimal.valueOf(150000));
        transactions.add(paidA2);

        PaymentTransaction refundA = new PaymentTransaction();
        refundA.setFilmId(filmA);
        refundA.setCinemaId(cinema2);
        refundA.setRefundedAt(now);
        refundA.setAmount(BigDecimal.valueOf(150000));
        transactions.add(refundA);

        PaymentTransaction paidB = new PaymentTransaction();
        paidB.setFilmId(filmB);
        paidB.setCinemaId(cinema1);
        paidB.setPaidAt(now);
        paidB.setAmount(BigDecimal.valueOf(90000));
        transactions.add(paidB);

        Map<UUID, String> filmNames = Map.of(
                filmA, "Film A",
                filmB, "Film B");

        List<FilmRevenueItemResponse> items = revenueReportSupport.aggregateFilmRevenueItems(
                transactions,
                filmNames,
                request);

        assertEquals(2, items.size());

        FilmRevenueItemResponse filmAItem = items.stream()
                .filter(item -> filmA.equals(item.filmId()))
                .findFirst()
                .orElse(null);
        assertNotNull(filmAItem);
        assertEquals("Film A", filmAItem.filmName());
        assertEquals(2L, filmAItem.cinemaCount());
        assertEquals(3L, filmAItem.totalTransactions());
        assertEquals(2L, filmAItem.paidCount());
        assertEquals(1L, filmAItem.refundedCount());
        assertEquals(BigDecimal.valueOf(250000).setScale(0), filmAItem.paidAmount());

        FilmRevenueItemResponse filmBItem = items.stream()
                .filter(item -> filmB.equals(item.filmId()))
                .findFirst()
                .orElse(null);
        assertNotNull(filmBItem);
        assertEquals("Film B", filmBItem.filmName());
        assertEquals(1L, filmBItem.cinemaCount());
        assertEquals(1L, filmBItem.totalTransactions());
        assertEquals(1L, filmBItem.paidCount());
        assertEquals(0L, filmBItem.refundedCount());
        assertEquals(BigDecimal.valueOf(90000).setScale(0), filmBItem.paidAmount());
    }
}
