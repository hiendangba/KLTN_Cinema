package com.cinema.http;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestAuthUtilsTest {

    @Test
    void requireRoleHeader_shouldUseHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_MANAGER);

        String role = RequestAuthUtils.requireRoleHeader(request);

        assertThat(role).isEqualTo(HeaderNames.ROLE_MANAGER);
    }

    @Test
    void requireUserId_shouldUseHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID userId = UUID.randomUUID();
        request.addHeader(HeaderNames.X_USER_ID, userId.toString());

        UUID resolvedUserId = RequestAuthUtils.requireUserId(request);

        assertThat(resolvedUserId).isEqualTo(userId);
    }

    @Test
    void requireUserId_shouldRejectMalformedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ID, "not-a-uuid");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> RequestAuthUtils.requireUserId(request, ErrorCode.INVALID_FORMAT));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_FORMAT);
    }

    @Test
    void requireRoleHeader_shouldRejectWhenMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> RequestAuthUtils.requireRoleHeader(request));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void requireRole_shouldAllowMatchingHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_MANAGER);

        assertDoesNotThrow(() ->
                RequestAuthUtils.requireRole(request, HeaderNames.ROLE_MANAGER));
    }

    @Test
    void requireAnyRole_shouldAllowRoleFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_MANAGER);

        assertDoesNotThrow(() ->
                RequestAuthUtils.requireAnyRole(request, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER));
    }

    @Test
    void requireAnyRole_shouldRejectWhenRoleDoesNotMatch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_CUSTOMER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> RequestAuthUtils.requireAnyRole(request, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }
}
