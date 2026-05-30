package com.cinema.http;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.UUID;

public final class RequestAuthUtils {

    private RequestAuthUtils() {
    }

    public static UUID requireUserId(HttpServletRequest request) {
        return requireUserId(request, ErrorCode.INVALID_FORMAT);
    }

    public static UUID requireUserId(HttpServletRequest request, ErrorCode invalidUserIdError) {
        String userIdRaw = trimToNull(request.getHeader(HeaderNames.X_USER_ID));
        if (userIdRaw == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return UUID.fromString(userIdRaw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(invalidUserIdError);
        }
    }

    public static String requireRoleHeader(HttpServletRequest request) {
        String role = trimToNull(request.getHeader(HeaderNames.X_USER_ROLE));
        if (role == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return role;
    }

    public static void requireRole(HttpServletRequest request, String requiredRole) {
        requireRole(request, requiredRole, null, null);
    }

    public static void requireRole(
            HttpServletRequest request,
            String requiredRole,
            Logger logger,
            String action) {
        String role = trimToNull(request.getHeader(HeaderNames.X_USER_ROLE));
        if (!requiredRole.equals(role)) {
            logForbiddenIfNeeded(logger, action, role, request, new String[]{requiredRole});
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public static void requireAnyRole(HttpServletRequest request, String... allowedRoles) {
        requireAnyRole(request, null, null, allowedRoles);
    }

    public static void requireAnyRole(
            HttpServletRequest request,
            Logger logger,
            String action,
            String... allowedRoles) {
        String role = trimToNull(request.getHeader(HeaderNames.X_USER_ROLE));
        boolean allowed = Arrays.stream(allowedRoles).anyMatch(allowedRole -> allowedRole.equals(role));
        if (!allowed) {
            logForbiddenIfNeeded(logger, action, role, request, allowedRoles);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void logForbiddenIfNeeded(
            Logger logger,
            String action,
            String actualRole,
            HttpServletRequest request,
            String[] requiredRoles) {
        if (logger == null) {
            return;
        }

        String required = String.join(",", requiredRoles);
        logger.warn(
                "Forbidden action={} requiredRoles={} actualRole={} userId={} method={} path={}",
                action,
                required,
                actualRole,
                resolveUserIdForLogging(request),
                request.getMethod(),
                request.getRequestURI());
    }

    private static String resolveUserIdForLogging(HttpServletRequest request) {
        return trimToNull(request.getHeader(HeaderNames.X_USER_ID));
    }
}
