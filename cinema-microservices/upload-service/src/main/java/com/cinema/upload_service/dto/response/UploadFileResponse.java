package com.cinema.upload_service.dto.response;

public record UploadFileResponse(
        String fileId,
        String originalFileName,
        String contentType,
        String mediaType,
        Long size,
        String url
) {
}
