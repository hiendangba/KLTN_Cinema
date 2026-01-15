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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    public String generateAccessToken(User user, String tokenId ) {
        return generateToken(user, accessTokenExpiration, "access", tokenId);
    }

    public String generateRefreshToken(User user,  String tokenId) {
        return generateToken(user, refreshTokenExpiration, "refresh", tokenId);
    }
    public String generateRefreshTokenWithExp(User user, String tokenId, Long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("tokenId", tokenId);
        claims.put("tokenType", "refresh");

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
}
