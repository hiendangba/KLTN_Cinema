package com.cinema.upload_service.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    @NotBlank
    private String publicRoot;

    @NotBlank
    private String tempRoot;

    @Min(1)
    private int imageMaxFileCount = 5;

    private DataSize imageMaxSize = DataSize.ofMegabytes(10);

    private DataSize videoMaxSize = DataSize.ofGigabytes(10);

    private DataSize videoChunkSize = DataSize.ofMegabytes(10);

    @Min(1)
    private long videoSessionTtlMinutes = 120;

    @NotBlank
    private String cleanupCron;

    private List<String> allowedImageContentTypes = new ArrayList<>();

    private List<String> allowedVideoContentTypes = new ArrayList<>();
}
