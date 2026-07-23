package com.cinema.upload_service.dto.response;

import java.util.List;

public record UploadFilesData(
        List<UploadFileResponse> files
) {
}
