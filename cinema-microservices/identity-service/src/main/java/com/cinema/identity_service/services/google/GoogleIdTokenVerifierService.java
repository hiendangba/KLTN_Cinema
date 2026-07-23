package com.cinema.identity_service.services.google;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GoogleIdTokenVerifierService {
    private static final Set<String> ALLOWED_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final Set<String> allowedClientIds;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierService(@Value("${app.auth.google.client-ids:}") String allowedClientIdsRaw) {
        this.allowedClientIds = Arrays.stream(allowedClientIdsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());

        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(this.allowedClientIds)
                .setIssuers(ALLOWED_ISSUERS)
                .build();
    }

    public GoogleUserInfo verify(String idTokenValue) {
        if (allowedClientIds.isEmpty()) {
            log.error("Google client IDs are not configured");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        GoogleIdToken idToken = verifyIdToken(idTokenValue);
        if (idToken == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String providerId = payload.getSubject();
        String email = payload.getEmail();
        Object emailVerifiedRaw = payload.get("email_verified");
        boolean emailVerified = emailVerifiedRaw instanceof Boolean value
                ? value
                : Boolean.parseBoolean(String.valueOf(emailVerifiedRaw));
        String issuer = payload.getIssuer();
        Long expirationSeconds = payload.getExpirationTimeSeconds();

        if (providerId == null || providerId.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!emailVerified) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!ALLOWED_ISSUERS.contains(issuer)) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (expirationSeconds == null || expirationSeconds <= Instant.now().getEpochSecond()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String name = payload.get("name") == null ? null : payload.get("name").toString();
        return new GoogleUserInfo(providerId, email, name);
    }

    private GoogleIdToken verifyIdToken(String idTokenValue) {
        try {
            return verifier.verify(idTokenValue);
        } catch (GeneralSecurityException | IOException ex) {
            log.warn("Google ID token verification failed", ex);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
    }
}
