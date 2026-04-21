package com.cinema.identity_service.config;

import com.cinema.http.HeaderNames;
import com.cinema.identity_service.services.impl.JwtServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtServiceImpl jwtService;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ACCESS_TOKEN_PREFIX = "identity:token:access:";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("Skip auth: invalid token type");


        String token = authHeader.substring(7);
        try {
            String tokenType = jwtService.extractTokenType(token);
            if (!"access".equals(tokenType)) {
                log.warn("Skip auth: invalid token type tokenType={} method={} path={}",
                        tokenType, request.getMethod(), request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            if (!jwtService.validateToken(token) || jwtService.isTokenExpired(token)) {
                log.warn("Skip auth: token invalid or expired method={} path={}",
                        request.getMethod(), request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            String tokenId = jwtService.extractTokenId(token);
            UUID userId = jwtService.extractUserId(token);

            String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
            Boolean hasAccessToken = redisTemplate.hasKey(accessTokenKey);
            if (!Boolean.TRUE.equals(hasAccessToken)) {
                log.warn("Skip auth: access token not found in Redis tokenId={} method={} path={}",
                        tokenId, request.getMethod(), request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            List<String> authorities = jwtService.extractAuthorities(token);
            List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,  // principal (email)
                            null,       // credentials (không cần password)
                            grantedAuthorities  // ← THÊM AUTHORITIES
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );
            request.setAttribute("userId", userId);
            request.setAttribute("tokenId", tokenId);
            request.setAttribute("role", jwtService.extractRole(token));
            response.setHeader(HeaderNames.X_USER_ID, userId.toString());
            response.setHeader(HeaderNames.X_USER_ROLE, jwtService.extractRole(token));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            log.warn("Skip auth: exception while parsing token method={} path={} reason={}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
