package com.cinema.identity_service.services.google;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class GoogleOAuthService {
    private static final String AUTHORIZATION_SERVER_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";
    private static final List<String> SCOPES = List.of("openid", "email", "profile");

    final GoogleIdTokenVerifierService googleIdTokenVerifierService;

    @Value("${app.auth.google.client-id:}")
    String googleClientId;
    @Value("${app.auth.google.client-ids:}")
    String googleClientIds;
    @Value("${app.auth.google.client-secret:}")
    String googleClientSecret;
    @Value("${app.auth.google.redirect-uri:}")
    String googleRedirectUri;

    public String buildAuthorizationUrl(String state) {
        GoogleAuthorizationCodeRequestUrl requestUrl = new GoogleAuthorizationCodeRequestUrl(
                AUTHORIZATION_SERVER_URL,
                resolveClientId(),
                requireValue(googleRedirectUri, "Google redirect URI"),
                SCOPES);
        return requestUrl
                .setState(state)
                .build();
    }

    public GoogleUserInfo exchangeCode(String code) {
        try {
            GoogleAuthorizationCodeTokenRequest tokenRequest = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    TOKEN_SERVER_URL,
                    resolveClientId(),
                    requireValue(googleClientSecret, "Google client secret"),
                    code);
            tokenRequest.setRedirectUri(requireValue(googleRedirectUri, "Google redirect URI"));

            GoogleTokenResponse tokenResponse = tokenRequest.execute();
            String idToken = tokenResponse.getIdToken();
            if (idToken == null || idToken.isBlank()) {
                throw new GoogleOAuthFlowException("token_invalid");
            }
            return googleIdTokenVerifierService.verify(idToken);
        } catch (GoogleOAuthFlowException ex) {
            throw ex;
        } catch (BusinessException ex) {
            log.warn("Google OAuth token verification failed");
            throw new GoogleOAuthFlowException("token_invalid", ex);
        } catch (IOException ex) {
            log.warn("Google OAuth code exchange failed", ex);
            throw new GoogleOAuthFlowException("code_exchange_failed", ex);
        }
    }

    private String resolveClientId() {
        String directClientId = trimToNull(googleClientId);
        if (directClientId != null) {
            return directClientId;
        }

        if (googleClientIds != null && !googleClientIds.isBlank()) {
            for (String candidate : googleClientIds.split(",")) {
                String trimmedCandidate = trimToNull(candidate);
                if (trimmedCandidate != null) {
                    return trimmedCandidate;
                }
            }
        }

        throw new BusinessException(ErrorCode.LOGIN_FAILED);
    }

    private String requireValue(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            log.error("{} is not configured", fieldName);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
