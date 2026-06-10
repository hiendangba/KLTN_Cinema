package com.cinema.upload_service.service.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.http.RequestAuthUtils;
import com.cinema.upload_service.config.UploadProperties;
import com.cinema.upload_service.dto.request.CreateVideoUploadSessionRequest;
import com.cinema.upload_service.dto.response.UploadFileResponse;
import com.cinema.upload_service.dto.response.VideoUploadSessionStatusData;
import com.cinema.upload_service.entity.StoredFile;
import com.cinema.upload_service.entity.VideoUploadChunk;
import com.cinema.upload_service.entity.VideoUploadSession;
import com.cinema.upload_service.entity.enums.MediaType;
import com.cinema.upload_service.entity.enums.StoredFileStatus;
import com.cinema.upload_service.entity.enums.VideoUploadSessionStatus;
import com.cinema.upload_service.repository.StoredFileRepository;
import com.cinema.upload_service.repository.VideoUploadChunkRepository;
import com.cinema.upload_service.repository.VideoUploadSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadServiceImpl implements com.cinema.upload_service.service.UploadService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv");

    private final UploadProperties uploadProperties;
    private final StoredFileRepository storedFileRepository;
    private final VideoUploadSessionRepository videoUploadSessionRepository;
    private final VideoUploadChunkRepository videoUploadChunkRepository;

    @Override
    @Transactional
    public ActionMessageResponse uploadImages(MultipartFile[] files, HttpServletRequest request) {
        UUID ownerUserId = RequestAuthUtils.resolveOptionalUserId(request);
        validateImageBatch(files);

        List<Path> writtenFiles = new ArrayList<>();
        try {
            ensureDirectory(Paths.get(uploadProperties.getPublicRoot()));

            List<StoredFile> storedFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                validateImageFile(file);
                StoredFile storedFile = buildStoredFile(ownerUserId, file, MediaType.IMAGE, request);
                Path targetPath = Paths.get(storedFile.getAbsolutePath());
                ensureDirectory(targetPath.getParent());
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                writtenFiles.add(targetPath);
                storedFiles.add(storedFile);
            }

            storedFileRepository.saveAll(storedFiles);
            return ActionMessageResponse.builder()
                    .message(SuccessMessage.UPLOAD_IMAGES_COMPLETED.getMessage())
                    .build();
        } catch (IOException ex) {
            cleanupFiles(writtenFiles);
            log.error("Failed to upload image batch", ex);
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        } catch (RuntimeException ex) {
            cleanupFiles(writtenFiles);
            throw ex;
        }
    }

    @Override
    @Transactional
    public ActionMessageResponse createVideoSession(
            CreateVideoUploadSessionRequest requestBody,
            HttpServletRequest request) {
        UUID ownerUserId = RequestAuthUtils.resolveOptionalUserId(request);
        validateVideoSessionRequest(requestBody);

        UUID sessionId = UUID.randomUUID();
        Path sessionDir = Paths.get(uploadProperties.getTempRoot()).resolve(sessionId.toString());
        ensureDirectory(sessionDir);

        long chunkSize = uploadProperties.getVideoChunkSize().toBytes();
        int totalChunks = (int) Math.ceil((double) requestBody.getSize() / chunkSize);

        VideoUploadSession session = VideoUploadSession.builder()
                .id(sessionId)
                .ownerUserId(ownerUserId)
                .originalFileName(requestBody.getOriginalFileName())
                .contentType(normalizeContentType(requestBody.getContentType()))
                .declaredSize(requestBody.getSize())
                .chunkSize(chunkSize)
                .totalChunks(totalChunks)
                .uploadedChunks(0)
                .tempDir(sessionDir.toString())
                .status(VideoUploadSessionStatus.INITIATED)
                .expiresAt(LocalDateTime.now().plusMinutes(uploadProperties.getVideoSessionTtlMinutes()))
                .createdAt(LocalDateTime.now())
                .build();
        videoUploadSessionRepository.save(session);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.VIDEO_UPLOAD_SESSION_CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse uploadVideoChunk(UUID sessionId, int chunkIndex, HttpServletRequest request) {
        VideoUploadSession session = requireVideoSession(sessionId);
        validateVideoSessionActive(session);
        validateChunkIndex(session, chunkIndex);

        long contentLength = request.getContentLengthLong();
        long maxChunkSize = uploadProperties.getVideoChunkSize().toBytes();
        if (contentLength > maxChunkSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        Path sessionDir = Paths.get(session.getTempDir());
        ensureDirectory(sessionDir);
        Path chunkPath = sessionDir.resolve(chunkIndex + ".part");

        try (InputStream inputStream = request.getInputStream()) {
            long copiedBytes = Files.copy(inputStream, chunkPath, StandardCopyOption.REPLACE_EXISTING);
            if (copiedBytes <= 0) {
                throw new BusinessException(ErrorCode.EMPTY_FILE);
            }
            if (copiedBytes > maxChunkSize) {
                Files.deleteIfExists(chunkPath);
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }

            VideoUploadChunk chunk = VideoUploadChunk.builder()
                    .sessionId(sessionId)
                    .chunkIndex(chunkIndex)
                    .chunkSize(copiedBytes)
                    .received(true)
                    .receivedAt(LocalDateTime.now())
                    .build();
            videoUploadChunkRepository.save(chunk);

            int uploadedChunks = (int) videoUploadChunkRepository.countBySessionIdAndReceivedTrue(sessionId);
            session.setUploadedChunks(uploadedChunks);
            session.setStatus(VideoUploadSessionStatus.UPLOADING);
            videoUploadSessionRepository.save(session);

            return ActionMessageResponse.builder()
                    .message(SuccessMessage.VIDEO_CHUNK_UPLOADED.getMessage())
                    .build();
        } catch (IOException ex) {
            log.error("Failed to upload video chunk sessionId={} chunkIndex={}", sessionId, chunkIndex, ex);
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public VideoUploadSessionStatusData getVideoSessionStatus(UUID sessionId, HttpServletRequest request) {
        VideoUploadSession session = requireVideoSession(sessionId);
        List<Integer> receivedChunks = videoUploadChunkRepository.findBySessionIdOrderByChunkIndexAsc(sessionId)
                .stream()
                .filter(VideoUploadChunk::isReceived)
                .map(VideoUploadChunk::getChunkIndex)
                .toList();

        return new VideoUploadSessionStatusData(
                session.getId().toString(),
                session.getStatus().name(),
                session.getUploadedChunks(),
                session.getTotalChunks(),
                receivedChunks,
                session.getExpiresAt());
    }

    @Override
    @Transactional
    public ActionMessageResponse completeVideoUpload(UUID sessionId, HttpServletRequest request) {
        VideoUploadSession session = requireVideoSession(sessionId);
        validateVideoSessionActive(session);

        List<VideoUploadChunk> chunks = videoUploadChunkRepository.findBySessionIdOrderByChunkIndexAsc(sessionId);
        if (chunks.size() != session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_INCOMPLETE);
        }

        session.setStatus(VideoUploadSessionStatus.COMPLETING);
        videoUploadSessionRepository.save(session);

        ensureDirectory(Paths.get(uploadProperties.getPublicRoot()));

        String extension = resolveExtension(session.getOriginalFileName(), MediaType.VIDEO);
        String objectKey = buildObjectKey(extension);
        Path finalPath = Paths.get(uploadProperties.getPublicRoot()).resolve(objectKey);
        ensureDirectory(finalPath.getParent());

        try (OutputStream outputStream = Files.newOutputStream(finalPath)) {
            long mergedSize = 0L;
            for (int expectedIndex = 0; expectedIndex < session.getTotalChunks(); expectedIndex++) {
                VideoUploadChunk chunk = chunks.get(expectedIndex);
                if (!Objects.equals(chunk.getChunkIndex(), expectedIndex) || !chunk.isReceived()) {
                    throw new BusinessException(ErrorCode.VIDEO_UPLOAD_INCOMPLETE);
                }
                Path chunkPath = Paths.get(session.getTempDir()).resolve(expectedIndex + ".part");
                if (!Files.exists(chunkPath)) {
                    throw new BusinessException(ErrorCode.VIDEO_UPLOAD_INCOMPLETE);
                }
                mergedSize += Files.copy(chunkPath, outputStream);
            }

            if (!Objects.equals(mergedSize, session.getDeclaredSize())) {
                Files.deleteIfExists(finalPath);
                session.setStatus(VideoUploadSessionStatus.FAILED);
                videoUploadSessionRepository.save(session);
                throw new BusinessException(ErrorCode.UPLOAD_FAILED);
            }

            StoredFile storedFile = StoredFile.builder()
                    .id(UUID.randomUUID())
                    .ownerUserId(session.getOwnerUserId())
                    .originalFileName(session.getOriginalFileName())
                    .contentType(session.getContentType())
                    .mediaType(MediaType.VIDEO)
                    .extension(extension)
                    .size(mergedSize)
                    .absolutePath(finalPath.toAbsolutePath().toString())
                    .objectKey(objectKey.replace('\\', '/'))
                    .publicUrl(buildPublicUrl(request, objectKey))
                    .status(StoredFileStatus.READY)
                    .createdAt(LocalDateTime.now())
                    .build();
            storedFileRepository.save(storedFile);

            session.setStatus(VideoUploadSessionStatus.READY);
            videoUploadSessionRepository.save(session);
            deleteDirectoryQuietly(Paths.get(session.getTempDir()));

            return ActionMessageResponse.builder()
                    .message(SuccessMessage.VIDEO_UPLOAD_COMPLETED.getMessage())
                    .build();
        } catch (IOException ex) {
            deleteFileQuietly(finalPath);
            session.setStatus(VideoUploadSessionStatus.FAILED);
            videoUploadSessionRepository.save(session);
            log.error("Failed to complete video upload sessionId={}", sessionId, ex);
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        }
    }

    public void cleanupExpiredVideoSessions() {
        List<VideoUploadSessionStatus> cleanupStatuses = List.of(
                VideoUploadSessionStatus.INITIATED,
                VideoUploadSessionStatus.UPLOADING,
                VideoUploadSessionStatus.FAILED,
                VideoUploadSessionStatus.EXPIRED);
        List<VideoUploadSession> expiredSessions = videoUploadSessionRepository.findByStatusInAndExpiresAtBefore(
                cleanupStatuses,
                LocalDateTime.now());

        for (VideoUploadSession session : expiredSessions) {
            deleteDirectoryQuietly(Paths.get(session.getTempDir()));
            session.setStatus(VideoUploadSessionStatus.EXPIRED);
        }

        if (!expiredSessions.isEmpty()) {
            videoUploadSessionRepository.saveAll(expiredSessions);
        }
    }

    private void validateImageBatch(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }
        if (files.length > uploadProperties.getImageMaxFileCount()) {
            throw new BusinessException(ErrorCode.TOO_MANY_FILES);
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!uploadProperties.getAllowedImageContentTypes().contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
        }
        if (file.getSize() > uploadProperties.getImageMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        resolveExtension(file.getOriginalFilename(), MediaType.IMAGE);
    }

    private void validateVideoSessionRequest(CreateVideoUploadSessionRequest requestBody) {
        String contentType = normalizeContentType(requestBody.getContentType());
        if (!uploadProperties.getAllowedVideoContentTypes().contains(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
        }
        if (requestBody.getSize() > uploadProperties.getVideoMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        resolveExtension(requestBody.getOriginalFileName(), MediaType.VIDEO);
    }

    private StoredFile buildStoredFile(
            UUID ownerUserId,
            MultipartFile file,
            MediaType mediaType,
            HttpServletRequest request) {
        String extension = resolveExtension(file.getOriginalFilename(), mediaType);
        String objectKey = buildObjectKey(extension);
        Path targetPath = Paths.get(uploadProperties.getPublicRoot()).resolve(objectKey);

        return StoredFile.builder()
                .id(UUID.randomUUID())
                .ownerUserId(ownerUserId)
                .originalFileName(file.getOriginalFilename())
                .contentType(normalizeContentType(file.getContentType()))
                .mediaType(mediaType)
                .extension(extension)
                .size(file.getSize())
                .absolutePath(targetPath.toAbsolutePath().toString())
                .objectKey(objectKey.replace('\\', '/'))
                .publicUrl(buildPublicUrl(request, objectKey))
                .status(StoredFileStatus.READY)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UploadFileResponse toUploadFileResponse(StoredFile storedFile) {
        return new UploadFileResponse(
                storedFile.getId().toString(),
                storedFile.getOriginalFileName(),
                storedFile.getContentType(),
                storedFile.getMediaType().name(),
                storedFile.getSize(),
                storedFile.getPublicUrl());
    }

    private VideoUploadSession requireVideoSession(UUID sessionId) {
        return videoUploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VIDEO_UPLOAD_SESSION_NOT_FOUND));
    }

    private void validateVideoSessionActive(VideoUploadSession session) {
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus(VideoUploadSessionStatus.EXPIRED);
            videoUploadSessionRepository.save(session);
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_SESSION_EXPIRED);
        }
        if (session.getStatus() == VideoUploadSessionStatus.READY
                || session.getStatus() == VideoUploadSessionStatus.COMPLETING
                || session.getStatus() == VideoUploadSessionStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_SESSION_INVALID_STATE);
        }
    }

    private void validateChunkIndex(VideoUploadSession session, int chunkIndex) {
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new BusinessException(ErrorCode.VIDEO_UPLOAD_CHUNK_OUT_OF_RANGE);
        }
    }

    private String resolveExtension(String originalFileName, MediaType mediaType) {
        String fileName = StringUtils.hasText(originalFileName) ? originalFileName.trim() : "";
        int lastDot = fileName.lastIndexOf('.');
        String extension = lastDot >= 0 ? fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT) : "";
        Set<String> allowedExtensions = mediaType == MediaType.IMAGE ? IMAGE_EXTENSIONS : VIDEO_EXTENSIONS;
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
        }
        return extension;
    }

    private String buildObjectKey(String extension) {
        LocalDate now = LocalDate.now();
        return Paths.get(
                        String.valueOf(now.getYear()),
                        String.format("%02d", now.getMonthValue()),
                        UUID.randomUUID() + "." + extension)
                .toString();
    }

    private String buildPublicUrl(HttpServletRequest request, String objectKey) {
        String normalizedKey = objectKey.replace('\\', '/');
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath("/media/" + normalizedKey)
                .replaceQuery(null)
                .build()
                .toUriString();
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private Double calculateProgress(int uploadedChunks, int totalChunks) {
        if (totalChunks <= 0) {
            return 0.0;
        }
        return Math.round((uploadedChunks * 10000.0) / totalChunks) / 100.0;
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            log.error("Failed to create directory {}", path, ex);
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        }
    }

    private void cleanupFiles(List<Path> files) {
        files.stream()
                .sorted(Comparator.reverseOrder())
                .forEach(this::deleteFileQuietly);
    }

    private void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Failed to delete file {}", path, ex);
        }
    }

    private void deleteDirectoryQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (DirectoryNotEmptyException ex) {
                            log.warn("Directory {} not empty during cleanup", path, ex);
                        } catch (IOException ex) {
                            log.warn("Failed to delete path {}", path, ex);
                        }
                    });
        } catch (IOException ex) {
            log.warn("Failed to walk cleanup directory {}", root, ex);
        }
    }
}
