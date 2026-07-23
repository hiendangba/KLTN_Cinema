package com.cinema.showtime_service.scheduler;

import com.cinema.showtime_service.config.ShowTimeStatusSchedulerProperties;
import com.cinema.showtime_service.services.ShowTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShowTimeStatusScheduler {

    private final ShowTimeService showTimeService;
    private final ShowTimeStatusSchedulerProperties properties;

    @Scheduled(cron = "${showtime.status-scheduler.scheduled-to-ongoing-cron:0 0 0 * * *}")
    public void promoteScheduledShowtimesToOngoing() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int updated = showTimeService.promoteScheduledShowtimesToOngoing(
                    now,
                    properties.scheduledToOngoingWindowDays());
            log.info("Scheduled showtime promotion completed. updated={}, now={}, windowDays={}",
                    updated,
                    now,
                    properties.scheduledToOngoingWindowDays());
        } catch (Exception ex) {
            log.error("Unexpected error while promoting scheduled showtimes to ongoing", ex);
        }
    }

    @Scheduled(fixedDelayString = "${showtime.status-scheduler.ongoing-to-finished-delay-ms:300000}")
    public void expireOngoingShowtimes() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int updated = showTimeService.expireOngoingShowtimes(now);
            log.info("Ongoing showtime expiration completed. updated={}, now={}", updated, now);
        } catch (Exception ex) {
            log.error("Unexpected error while expiring ongoing showtimes", ex);
        }
    }
}
