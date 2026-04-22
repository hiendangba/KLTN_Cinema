package com.cinema.identity_service.config;

import com.cinema.http.HeaderNames;
import com.cinema.identity_service.services.impl.JwtServiceImpl;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtServiceImpl jwtService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateFromAccessTokenCookie() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, redisTemplate);
        UUID userId = UUID.randomUUID();
        String token = "cookie-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/auth/manager");
        request.setCookies(new Cookie("accessToken", token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractTokenType(token)).thenReturn("access");
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.isTokenExpired(token)).thenReturn(false);
        when(jwtService.extractTokenId(token)).thenReturn("token-id");
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(redisTemplate.hasKey("identity:token:access:token-id")).thenReturn(true);
        when(jwtService.extractAuthorities(token)).thenReturn(List.of("ROLE_CUSTOMER"));
        when(jwtService.extractRole(token)).thenReturn("CUSTOMER");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
        assertThat(response.getHeader(HeaderNames.X_USER_ID)).isEqualTo(userId.toString());
        assertThat(response.getHeader(HeaderNames.X_USER_ROLE)).isEqualTo("CUSTOMER");
    }

    @Test
    void shouldFallbackToAuthorizationHeaderWhenCookieMissing() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, redisTemplate);
        UUID userId = UUID.randomUUID();
        String token = "header-token";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/internal/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractTokenType(token)).thenReturn("access");
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.isTokenExpired(token)).thenReturn(false);
        when(jwtService.extractTokenId(token)).thenReturn("token-id");
        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(redisTemplate.hasKey("identity:token:access:token-id")).thenReturn(true);
        when(jwtService.extractAuthorities(token)).thenReturn(List.of("ROLE_MANAGER"));
        when(jwtService.extractRole(token)).thenReturn("MANAGER");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(jwtService).extractTokenType(token);
    }

    @Test
    void shouldSkipAuthenticationWhenNoTokenProvided() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, redisTemplate);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/auth/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
        verify(redisTemplate, never()).hasKey(org.mockito.ArgumentMatchers.anyString());
    }
}
