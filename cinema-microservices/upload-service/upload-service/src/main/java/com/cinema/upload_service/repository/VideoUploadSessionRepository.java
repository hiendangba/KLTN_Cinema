package com.cinema.upload_service.repository;

import com.cinema.upload_service.entity.VideoUploadSession;
import com.cinema.upload_service.entity.enums.VideoUploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface VideoUploadSessionRepository extends JpaRepository<VideoUploadSession, UUID> {

    List<VideoUploadSession> findByStatusInAndExpiresAtBefore(
            List<VideoUploadSessionStatus> statuses,
            LocalDateTime expiresAt);
}
