package com.cinema.upload_service.dto.response;

public record VideoChunkUploadData(
        String sessionId,
        Integer chunkIndex,
        Long chunkSize,
        Integer uploadedChunks,
        Integer totalChunks,
        Double progressPercent
) {
}
