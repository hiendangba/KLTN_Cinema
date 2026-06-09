package com.cinema.upload_service.scheduler;

import com.cinema.upload_service.service.impl.UploadServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VideoUploadCleanupScheduler {

    private final UploadServiceImpl uploadService;

    @Scheduled(cron = "${upload.cleanup-cron}")
    public void cleanupExpiredVideoSessions() {
        log.debug("Running expired video upload session cleanup");
        uploadService.cleanupExpiredVideoSessions();
    }
}
