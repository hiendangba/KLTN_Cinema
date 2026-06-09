package com.cinema.upload_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateVideoUploadSessionRequest {

    @NotBlank(message = "originalFileName is required")
    private String originalFileName;

    @NotBlank(message = "contentType is required")
    private String contentType;

    @NotNull(message = "size is required")
    @Min(value = 1, message = "size must be greater than 0")
    private Long size;
}
