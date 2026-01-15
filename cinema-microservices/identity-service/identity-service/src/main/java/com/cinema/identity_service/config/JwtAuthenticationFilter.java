package com.cinema.identity_service.config;

import com.cinema.exception.BusinessException;
import com.cinema.identity_service.services.impl.JwtServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.UUID;
import java.util.Collections;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import java.io.IOException;

import static com.cinema.exception.ErrorCode.REFRESH_TOKEN_MISSING;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtServiceImpl jwtService;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ACCESS_TOKEN_PREFIX = "token:access:";
    private static final String USER_TOKENS_PREFIX = "user_tokens:";
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            String tokenType = jwtService.extractTokenType(token);
            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }
            String tokenId = jwtService.extractTokenId(token);
            UUID userId = jwtService.extractUserId(token);

            String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
            Boolean hasAccessToken = redisTemplate.hasKey(accessTokenKey);
            if (!Boolean.TRUE.equals(hasAccessToken)) {
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            token,
                            Collections.emptyList()
                    );

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
