package com.cinema.upload_service.dto.response;

import java.time.LocalDateTime;

public record VideoUploadSessionData(
        String sessionId,
        Long chunkSize,
        LocalDateTime expiresAt
) {
}
