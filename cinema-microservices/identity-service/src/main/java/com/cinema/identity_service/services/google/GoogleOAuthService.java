package com.cinema.identity_service.services.google;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.List;
import java.util.Locale;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class GoogleOAuthService {
    private static final String AUTHORIZATION_SERVER_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";
    private static final List<String> SCOPES = List.of("openid", "email", "profile");

    final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    final RestOperations restOperations;

    public GoogleOAuthService(GoogleIdTokenVerifierService googleIdTokenVerifierService) {
        this(googleIdTokenVerifierService, new RestTemplate());
    }

    GoogleOAuthService(GoogleIdTokenVerifierService googleIdTokenVerifierService, RestOperations restOperations) {
        this.googleIdTokenVerifierService = googleIdTokenVerifierService;
        this.restOperations = restOperations;
    }

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
            ResponseEntity<String> tokenResponse = exchangeToken(code, clientId, clientSecret, redirectUri);
            String idToken = extractIdToken(tokenResponse.getBody());
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
        } catch (RestClientResponseException ex) {
            String rawResponseBody = sanitizeForLog(ex.getResponseBodyAsString());
            String error = extractJsonField(ex.getResponseBodyAsString(), "error");
            String errorDescription = extractJsonField(ex.getResponseBodyAsString(), "error_description");
            String wwwAuthenticate = extractHeaderValue(ex.getResponseHeaders(), "WWW-Authenticate");
            log.warn(
                    "Google OAuth code exchange failed: statusCode={} error={} errorDescription={} wwwAuthenticate={} rawResponseBody={} tokenEndpoint={}",
                    ex.getRawStatusCode(),
                    error,
                    errorDescription,
                    wwwAuthenticate,
                    rawResponseBody,
                    TOKEN_SERVER_URL,
                    ex);
            throw new GoogleOAuthFlowException("code_exchange_failed", ex);
        } catch (ResourceAccessException ex) {
            log.warn("Google OAuth code exchange IO failure: codePresent={} codeLength={} reason={}",
                    hasText(code), safeLength(code), ex.getMessage(), ex);
            throw new GoogleOAuthFlowException("code_exchange_failed", ex);
        }
    }

    private ResponseEntity<String> exchangeToken(String code, String clientId, String clientSecret, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        return restOperations.postForEntity(TOKEN_SERVER_URL, request, String.class);
    }

    private String extractIdToken(String responseBody) {
        if (!hasText(responseBody)) {
            return null;
        }

        Map<String, Object> payload = parseJsonAsMap(responseBody);
        Object idToken = payload.get("id_token");
        return idToken == null ? null : String.valueOf(idToken);
    }

    private String extractJsonField(String json, String field) {
        if (!hasText(json) || !hasText(field)) {
            return null;
        }
        Map<String, Object> payload = parseJsonAsMap(json);
        Object value = payload.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> parseJsonAsMap(String json) {
        try {
            return JsonParserFactory.getJsonParser().parseMap(json);
        } catch (RuntimeException ignored) {
            return Map.of();
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

    private String extractHeaderValue(HttpHeaders headers, String headerName) {
        if (headers == null || headerName == null || headerName.isBlank()) {
            return null;
        }
        Object headerValue = headers.get(headerName);
        if (headerValue == null) {
            headerValue = headers.get(headerName.toLowerCase(Locale.ROOT));
        }
        return headerValue == null ? null : sanitizeForLog(String.valueOf(headerValue));
    }
}
