package com.cinema.upload_service.controller;

import com.cinema.Enum.SuccessMessage;
import com.cinema.controller.BaseController;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.upload_service.dto.request.CreateVideoUploadSessionRequest;
import com.cinema.upload_service.dto.response.VideoUploadSessionStatusData;
import com.cinema.upload_service.service.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController extends BaseController {

    private final UploadService uploadService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<APIResponse<ActionMessageResponse>> uploadImages(
            @RequestParam("files") MultipartFile[] files,
            HttpServletRequest request) {
        ActionMessageResponse data = uploadService.uploadImages(files, request);
        return created(SuccessMessage.UPLOAD_IMAGES_COMPLETED, data);
    }

    @PostMapping("/videos/sessions")
    public ResponseEntity<APIResponse<ActionMessageResponse>> createVideoSession(
            @Valid @RequestBody CreateVideoUploadSessionRequest requestBody,
            HttpServletRequest request) {
        ActionMessageResponse data = uploadService.createVideoSession(requestBody, request);
        return created(SuccessMessage.VIDEO_UPLOAD_SESSION_CREATED, data);
    }

    @PutMapping(value = "/videos/sessions/{sessionId}/chunks/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<APIResponse<ActionMessageResponse>> uploadVideoChunk(
            @PathVariable UUID sessionId,
            @PathVariable int index,
            HttpServletRequest request) {
        ActionMessageResponse data = uploadService.uploadVideoChunk(sessionId, index, request);
        return ok(SuccessMessage.VIDEO_CHUNK_UPLOADED, data);
    }

    @GetMapping("/videos/sessions/{sessionId}")
    public ResponseEntity<APIResponse<VideoUploadSessionStatusData>> getVideoSessionStatus(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        VideoUploadSessionStatusData data = uploadService.getVideoSessionStatus(sessionId, request);
        return ok(SuccessMessage.VIDEO_UPLOAD_STATUS_FETCHED, data);
    }

    @PostMapping("/videos/sessions/{sessionId}/complete")
    public ResponseEntity<APIResponse<ActionMessageResponse>> completeVideoUpload(
            @PathVariable UUID sessionId,
            HttpServletRequest request) {
        ActionMessageResponse data = uploadService.completeVideoUpload(sessionId, request);
        return ok(SuccessMessage.VIDEO_UPLOAD_COMPLETED, data);
    }
}
