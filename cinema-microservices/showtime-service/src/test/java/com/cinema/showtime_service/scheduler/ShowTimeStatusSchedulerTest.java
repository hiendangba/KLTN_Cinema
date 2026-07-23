package com.cinema.showtime_service.scheduler;

import com.cinema.showtime_service.config.ShowTimeStatusSchedulerProperties;
import com.cinema.showtime_service.services.ShowTimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShowTimeStatusSchedulerTest {

    @Mock
    private ShowTimeService showTimeService;

    @Test
    void properties_shouldClampWindowDaysToAtLeastOne() {
        ShowTimeStatusSchedulerProperties properties = new ShowTimeStatusSchedulerProperties(0);

        assertEquals(1, properties.scheduledToOngoingWindowDays());
    }

    @Test
    void promoteScheduledShowtimesToOngoing_shouldCallService() {
        ShowTimeStatusScheduler scheduler = new ShowTimeStatusScheduler(
                showTimeService,
                new ShowTimeStatusSchedulerProperties(7));

        scheduler.promoteScheduledShowtimesToOngoing();

        verify(showTimeService).promoteScheduledShowtimesToOngoing(any(LocalDateTime.class), anyInt());
    }

    @Test
    void expireOngoingShowtimes_shouldCallService() {
        ShowTimeStatusScheduler scheduler = new ShowTimeStatusScheduler(
                showTimeService,
                new ShowTimeStatusSchedulerProperties(7));

        scheduler.expireOngoingShowtimes();

        verify(showTimeService).expireOngoingShowtimes(any(LocalDateTime.class));
    }
}
