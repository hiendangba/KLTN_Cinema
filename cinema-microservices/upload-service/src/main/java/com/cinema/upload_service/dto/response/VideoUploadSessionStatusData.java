package com.cinema.upload_service.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record VideoUploadSessionStatusData(
        String sessionId,
        String status,
        Integer uploadedChunks,
        Integer totalChunks,
        List<Integer> receivedChunks,
        LocalDateTime expiresAt
) {
}
