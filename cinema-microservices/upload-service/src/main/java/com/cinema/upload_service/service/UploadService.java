package com.cinema.upload_service.service;

import com.cinema.upload_service.dto.request.CreateVideoUploadSessionRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.upload_service.dto.response.VideoUploadSessionStatusData;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UploadService {

    ActionMessageResponse uploadImages(MultipartFile[] files, HttpServletRequest request);

    ActionMessageResponse createVideoSession(CreateVideoUploadSessionRequest requestBody, HttpServletRequest request);

    ActionMessageResponse uploadVideoChunk(UUID sessionId, int chunkIndex, HttpServletRequest request);

    VideoUploadSessionStatusData getVideoSessionStatus(UUID sessionId, HttpServletRequest request);

    ActionMessageResponse completeVideoUpload(UUID sessionId, HttpServletRequest request);
}
