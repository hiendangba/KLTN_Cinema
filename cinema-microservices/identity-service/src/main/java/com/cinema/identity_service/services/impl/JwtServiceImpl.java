package com.cinema.identity_service.services.impl;

import com.cinema.identity_service.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.*;

@Getter
@Setter
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtServiceImpl {
    @Value("${spring.jwt.secret}")
    private String secretKey;
    @Value("${spring.jwt.access-token-expiration}")
    private Long accessTokenExpiration;
    @Value("${spring.jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user, String tokenId) {
        return generateToken(user, accessTokenExpiration, "access", tokenId);
    }

    public String generateRefreshToken(User user, String tokenId) {
        return generateToken(user, refreshTokenExpiration, "refresh", tokenId);
    }

    public String generateRefreshTokenWithExp(User user, String tokenId, Long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("tokenId", tokenId);
        claims.put("tokenType", "refresh");
        claims.put("authorities", Collections.singletonList("ROLE_" + user.getRole().name()));

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private String generateToken(User user, Long expiration, String tokenType, String tokenId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("tokenId", tokenId);
        claims.put("tokenType", tokenType);
        claims.put("authorities", Collections.singletonList("ROLE_" + user.getRole().name()));

        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractTokenId(String token) {
        return extractAllClaims(token).get("tokenId", String.class);
    }

    public String extractTokenType(String token) {
        return extractAllClaims(token).get("tokenType", String.class);
    }

    public UUID extractUserId(String token) {
        String userId = extractAllClaims(token).get("userId", String.class);
        return UUID.fromString(userId);
    }

    public long extractExpirationMillis(String token) {
        Date exp = extractAllClaims(token).getExpiration();
        return exp.getTime();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);
        Object authoritiesObj = claims.get("authorities");
        if (authoritiesObj instanceof List) {
            return (List<String>) authoritiesObj;
        }
        return Collections.emptyList();
    }

    public String extractRole(String token) {
        List<String> authorities = extractAuthorities(token);
        if (!authorities.isEmpty()) {
            String firstAuthority = authorities.get(0);
            if (firstAuthority.startsWith("ROLE_")) {
                return firstAuthority.substring(5);  // "ROLE_ADMIN" → "ADMIN"
            }
            return firstAuthority;
        }
        return null;
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractAllClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}