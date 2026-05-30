package com.cinema.identity_service.services.google;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.auth.oauth2.TokenErrorResponse;
import com.google.api.client.auth.oauth2.TokenResponseException;
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
        String clientId = resolveClientId();
        String redirectUri = requireValue(googleRedirectUri, "Google redirect URI");
        log.info("Google OAuth authorize init: statePresent={} stateLength={} clientId={} redirectUri={} scopes={}",
                hasText(state),
                safeLength(state),
                maskClientId(clientId),
                redirectUri,
                SCOPES);
        GoogleAuthorizationCodeRequestUrl requestUrl = new GoogleAuthorizationCodeRequestUrl(
                AUTHORIZATION_SERVER_URL,
                clientId,
                redirectUri,
                SCOPES);
        String authorizationUrl = requestUrl
                .setState(state)
                .build();
        log.info("Google OAuth authorize URL built: host={} stateLength={}",
                AUTHORIZATION_SERVER_URL, safeLength(state));
        return authorizationUrl;
    }

    public GoogleUserInfo exchangeCode(String code) {
        String clientId = resolveClientId();
        String clientSecret = requireValue(googleClientSecret, "Google client secret");
        String redirectUri = requireValue(googleRedirectUri, "Google redirect URI");
        log.info(
                "Google OAuth exchange start: codePresent={} codeLength={} clientId={} redirectUri={} clientSecretConfigured={}",
                hasText(code),
                safeLength(code),
                maskClientId(clientId),
                redirectUri,
                hasText(clientSecret));
        try {
            GoogleAuthorizationCodeTokenRequest tokenRequest = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    TOKEN_SERVER_URL,
                    clientId,
                    clientSecret,
                    code);
            tokenRequest.setRedirectUri(redirectUri);

            GoogleTokenResponse tokenResponse = tokenRequest.execute();
            String idToken = tokenResponse.getIdToken();
            if (idToken == null || idToken.isBlank()) {
                throw new GoogleOAuthFlowException("token_invalid");
            }
            log.info("Google OAuth exchange success: idTokenPresent={} idTokenLength={}",
                    hasText(idToken), safeLength(idToken));
            return googleIdTokenVerifierService.verify(idToken);
        } catch (GoogleOAuthFlowException ex) {
            throw ex;
        } catch (BusinessException ex) {
            log.warn("Google OAuth token verification failed: codePresent={} codeLength={} reason={}",
                    hasText(code), safeLength(code), ex.getErrorCode(), ex);
            throw new GoogleOAuthFlowException("token_invalid", ex);
        } catch (TokenResponseException ex) {
            TokenErrorResponse details = ex.getDetails();
            log.warn(
                    "Google OAuth code exchange failed: statusCode={} error={} errorDescription={} responseBody={}",
                    ex.getStatusCode(),
                    details == null ? null : details.getError(),
                    details == null ? null : details.getErrorDescription(),
                    sanitizeForLog(ex.getContent()),
                    ex);
            throw new GoogleOAuthFlowException("code_exchange_failed", ex);
        } catch (IOException ex) {
            log.warn("Google OAuth code exchange IO failure: codePresent={} codeLength={} reason={}",
                    hasText(code), safeLength(code), ex.getMessage(), ex);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String maskClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "<empty>";
        }
        if (clientId.length() <= 10) {
            return "***";
        }
        return clientId.substring(0, 6) + "..." + clientId.substring(clientId.length() - 4);
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        String oneLine = value.replaceAll("[\\r\\n]+", " ").trim();
        if (oneLine.length() <= 500) {
            return oneLine;
        }
        return oneLine.substring(0, 500) + "...";
    }
}
