package com.cinema.review_service.util;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.review_service.entity.enums.MediaType;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MediaUrlUtils {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv");

    private MediaUrlUtils() {
    }

    public static List<MediaDescriptor> normalize(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return List.of();
        }

        List<MediaDescriptor> result = new ArrayList<>(mediaUrls.size());
        for (String mediaUrl : mediaUrls) {
            String normalized = normalizeUrl(mediaUrl);
            result.add(new MediaDescriptor(normalized, resolveMediaType(normalized)));
        }
        return result;
    }

    public static MediaType resolveMediaType(String mediaUrl) {
        String path;
        try {
            URI uri = URI.create(mediaUrl);
            path = uri.getPath();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
        }

        if (!StringUtils.hasText(path)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
        }

        int lastSlash = path.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
        }

        String extension = fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return MediaType.IMAGE;
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return MediaType.VIDEO;
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE_UPLOAD);
    }

    private static String normalizeUrl(String mediaUrl) {
        if (!StringUtils.hasText(mediaUrl)) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
        }
        return mediaUrl.trim();
    }

    public record MediaDescriptor(String mediaUrl, MediaType mediaType) {
    }
}
