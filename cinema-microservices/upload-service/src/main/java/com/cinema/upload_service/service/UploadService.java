package com.cinema.upload_service.service;

import com.cinema.upload_service.dto.request.CreateVideoUploadSessionRequest;
import com.cinema.upload_service.dto.response.UploadFilesData;
import com.cinema.upload_service.dto.response.UploadVideoCompleteData;
import com.cinema.upload_service.dto.response.VideoChunkUploadData;
import com.cinema.upload_service.dto.response.VideoUploadSessionData;
import com.cinema.upload_service.dto.response.VideoUploadSessionStatusData;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UploadService {

    UploadFilesData uploadImages(MultipartFile[] files, HttpServletRequest request);

    VideoUploadSessionData createVideoSession(CreateVideoUploadSessionRequest requestBody, HttpServletRequest request);

    VideoChunkUploadData uploadVideoChunk(UUID sessionId, int chunkIndex, HttpServletRequest request);

    VideoUploadSessionStatusData getVideoSessionStatus(UUID sessionId, HttpServletRequest request);

    UploadVideoCompleteData completeVideoUpload(UUID sessionId, HttpServletRequest request);
}
