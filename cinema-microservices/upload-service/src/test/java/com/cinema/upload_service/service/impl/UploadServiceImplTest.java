package com.cinema.upload_service.service.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.upload_service.config.UploadProperties;
import com.cinema.upload_service.dto.request.CreateVideoUploadSessionRequest;
import com.cinema.upload_service.dto.response.VideoUploadSessionData;
import com.cinema.upload_service.entity.StoredFile;
import com.cinema.upload_service.entity.VideoUploadChunk;
import com.cinema.upload_service.entity.VideoUploadSession;
import com.cinema.upload_service.entity.enums.VideoUploadSessionStatus;
import com.cinema.upload_service.repository.StoredFileRepository;
import com.cinema.upload_service.repository.VideoUploadChunkRepository;
import com.cinema.upload_service.repository.VideoUploadSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    @Mock
    private StoredFileRepository storedFileRepository;

    @Mock
    private VideoUploadSessionRepository videoUploadSessionRepository;

    @Mock
    private VideoUploadChunkRepository videoUploadChunkRepository;

    @TempDir
    Path tempDir;

    private UploadProperties uploadProperties;
    private UploadServiceImpl uploadService;

    @BeforeEach
    void setUp() {
        uploadProperties = new UploadProperties();
        uploadProperties.setPublicRoot(tempDir.resolve("public").toString());
        uploadProperties.setTempRoot(tempDir.resolve("tmp").toString());
        uploadProperties.setImageMaxFileCount(5);
        uploadProperties.setImageMaxSize(DataSize.ofMegabytes(10));
        uploadProperties.setVideoMaxSize(DataSize.ofGigabytes(10));
        uploadProperties.setVideoChunkSize(DataSize.ofMegabytes(20));
        uploadProperties.setVideoSessionTtlMinutes(120);
        uploadProperties.setAllowedImageContentTypes(List.of("image/jpeg", "image/png", "image/webp", "image/gif"));
        uploadProperties.setAllowedVideoContentTypes(List.of("video/mp4", "video/quicktime", "video/x-msvideo", "video/x-matroska"));
        uploadService = new UploadServiceImpl(
                uploadProperties,
                storedFileRepository,
                videoUploadSessionRepository,
                videoUploadChunkRepository);
    }

    @Test
    void uploadImages_shouldStoreFilesAndReturnDataFiles() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "poster.jpg",
                "image/jpeg",
                "fake-image".getBytes());
        ArgumentCaptor<List<StoredFile>> captor = ArgumentCaptor.forClass(List.class);
        when(storedFileRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse result = uploadService.uploadImages(
                new MockMultipartFile[]{file},
                mockRequest("/api/uploads/images"));

        assertEquals(SuccessMessage.UPLOAD_IMAGES_COMPLETED.getMessage(), result.getMessage());

        Path publicRoot = tempDir.resolve("public");
        assertTrue(Files.exists(publicRoot));
        assertTrue(Files.walk(publicRoot).anyMatch(Files::isRegularFile));
        org.mockito.Mockito.verify(storedFileRepository).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getOwnerUserId());
    }

    @Test
    void uploadImages_shouldRejectWhenMoreThanFiveFiles() {
        MockMultipartFile[] files = new MockMultipartFile[6];
        for (int i = 0; i < files.length; i++) {
            files[i] = new MockMultipartFile("files", "image-" + i + ".jpg", "image/jpeg", "img".getBytes());
        }

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> uploadService.uploadImages(files, mockRequest("/api/uploads/images")));

        assertEquals(ErrorCode.TOO_MANY_FILES, exception.getErrorCode());
    }

    @Test
    void createVideoSession_shouldPersistSessionAndReturnChunkInfo() {
        CreateVideoUploadSessionRequest request = new CreateVideoUploadSessionRequest();
        request.setOriginalFileName("movie.mp4");
        request.setContentType("video/mp4");
        request.setSize(DataSize.ofMegabytes(45).toBytes());

        ArgumentCaptor<VideoUploadSession> captor = ArgumentCaptor.forClass(VideoUploadSession.class);
        when(videoUploadSessionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = uploadService.createVideoSession(
                request,
                mockRequest("/api/uploads/videos/sessions"));

        assertEquals(SuccessMessage.VIDEO_UPLOAD_SESSION_CREATED.getMessage(), response.getMessage());
        assertTrue(Files.exists(tempDir.resolve("tmp")));
        assertEquals(3, captor.getValue().getTotalChunks());
    }

    @Test
    void createVideoSession_shouldStoreOwnerWhenUserHeaderExists() {
        CreateVideoUploadSessionRequest request = new CreateVideoUploadSessionRequest();
        request.setOriginalFileName("movie.mp4");
        request.setContentType("video/mp4");
        request.setSize(DataSize.ofMegabytes(1).toBytes());

        ArgumentCaptor<VideoUploadSession> captor = ArgumentCaptor.forClass(VideoUploadSession.class);
        when(videoUploadSessionRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        uploadService.createVideoSession(
                request,
                mockRequestWithUserId("/api/uploads/videos/sessions", UUID.fromString("11111111-1111-1111-1111-111111111111")));

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), captor.getValue().getOwnerUserId());
    }

    @Test
    void uploadVideoChunk_shouldPersistChunkAndReturnProgress() {
        UUID sessionId = UUID.randomUUID();
        VideoUploadSession session = VideoUploadSession.builder()
                .id(sessionId)
                .ownerUserId(null)
                .originalFileName("movie.mp4")
                .contentType("video/mp4")
                .declaredSize(1024L)
                .chunkSize(uploadProperties.getVideoChunkSize().toBytes())
                .totalChunks(2)
                .uploadedChunks(0)
                .tempDir(tempDir.resolve("tmp").resolve(sessionId.toString()).toString())
                .status(VideoUploadSessionStatus.INITIATED)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();
        when(videoUploadSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(videoUploadChunkRepository.save(any(VideoUploadChunk.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoUploadChunkRepository.countBySessionIdAndReceivedTrue(sessionId)).thenReturn(1L);
        when(videoUploadSessionRepository.save(any(VideoUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = mockRequest("/api/uploads/videos/sessions/" + sessionId + "/chunks/0");
        request.setContentType("application/octet-stream");
        request.setContent("chunk-data".getBytes());

        ActionMessageResponse response = uploadService.uploadVideoChunk(sessionId, 0, request);

        assertEquals(SuccessMessage.VIDEO_CHUNK_UPLOADED.getMessage(), response.getMessage());
        assertTrue(Files.exists(Path.of(session.getTempDir()).resolve("0.part")));
    }

    @Test
    void completeVideoUpload_shouldMergeChunksAndReturnPublicFile() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Path sessionDir = tempDir.resolve("tmp").resolve(sessionId.toString());
        Files.createDirectories(sessionDir);
        Files.write(sessionDir.resolve("0.part"), "hello-".getBytes());
        Files.write(sessionDir.resolve("1.part"), "world".getBytes());

        VideoUploadSession session = VideoUploadSession.builder()
                .id(sessionId)
                .ownerUserId(null)
                .originalFileName("movie.mp4")
                .contentType("video/mp4")
                .declaredSize(11L)
                .chunkSize(uploadProperties.getVideoChunkSize().toBytes())
                .totalChunks(2)
                .uploadedChunks(2)
                .tempDir(sessionDir.toString())
                .status(VideoUploadSessionStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();

        when(videoUploadSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(videoUploadChunkRepository.findBySessionIdOrderByChunkIndexAsc(sessionId)).thenReturn(List.of(
                VideoUploadChunk.builder().sessionId(sessionId).chunkIndex(0).chunkSize(6L).received(true).receivedAt(LocalDateTime.now()).build(),
                VideoUploadChunk.builder().sessionId(sessionId).chunkIndex(1).chunkSize(5L).received(true).receivedAt(LocalDateTime.now()).build()
        ));
        when(videoUploadSessionRepository.save(any(VideoUploadSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storedFileRepository.save(any(StoredFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = uploadService.completeVideoUpload(
                sessionId,
                mockRequest("/api/uploads/videos/sessions/" + sessionId + "/complete"));

        assertEquals(SuccessMessage.VIDEO_UPLOAD_COMPLETED.getMessage(), response.getMessage());
        Path publicRoot = tempDir.resolve("public");
        assertTrue(Files.walk(publicRoot).anyMatch(Files::isRegularFile));
        assertTrue(Files.notExists(sessionDir));
    }

    @Test
    void completeVideoUpload_shouldRejectWhenChunksMissing() {
        UUID sessionId = UUID.randomUUID();
        VideoUploadSession session = VideoUploadSession.builder()
                .id(sessionId)
                .ownerUserId(null)
                .originalFileName("movie.mp4")
                .contentType("video/mp4")
                .declaredSize(100L)
                .chunkSize(uploadProperties.getVideoChunkSize().toBytes())
                .totalChunks(2)
                .uploadedChunks(1)
                .tempDir(tempDir.resolve("tmp").resolve(sessionId.toString()).toString())
                .status(VideoUploadSessionStatus.UPLOADING)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();

        when(videoUploadSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(videoUploadChunkRepository.findBySessionIdOrderByChunkIndexAsc(sessionId)).thenReturn(List.of(
                VideoUploadChunk.builder().sessionId(sessionId).chunkIndex(0).chunkSize(50L).received(true).receivedAt(LocalDateTime.now()).build()
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> uploadService.completeVideoUpload(sessionId, mockRequest("/api/uploads/videos/sessions/" + sessionId + "/complete")));

        assertEquals(ErrorCode.VIDEO_UPLOAD_INCOMPLETE, exception.getErrorCode());
    }

    private MockHttpServletRequest mockRequest(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("cinema.example.com");
        request.setServerPort(443);
        request.setRequestURI(requestUri);
        return request;
    }

    private MockHttpServletRequest mockRequestWithUserId(String requestUri, UUID userId) {
        MockHttpServletRequest request = mockRequest(requestUri);
        request.addHeader("X-User-ID", userId.toString());
        return request;
    }
}
