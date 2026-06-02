package com.cinema.showtime_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "showtime.status-scheduler")
public record ShowTimeStatusSchedulerProperties(
        int scheduledToOngoingWindowDays
) {
    public int scheduledToOngoingWindowDays() {
        return Math.max(1, scheduledToOngoingWindowDays);
    }
}
