package com.cinema.identity_service.services.google;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.allOf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CLIENT_ID = "client-id.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "client-secret";
    private static final String REDIRECT_URI = "https://cinema-api.duckdns.org/api/auth/google/callback";

    @Mock
    private GoogleIdTokenVerifierService googleIdTokenVerifierService;

    private GoogleOAuthService service;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        service = new GoogleOAuthService(googleIdTokenVerifierService, restTemplate);

        ReflectionTestUtils.setField(service, "googleClientId", CLIENT_ID);
        ReflectionTestUtils.setField(service, "googleClientIds", CLIENT_ID);
        ReflectionTestUtils.setField(service, "googleClientSecret", CLIENT_SECRET);
        ReflectionTestUtils.setField(service, "googleRedirectUri", REDIRECT_URI);
    }

    @Test
    void exchangeCode_shouldReturnGoogleUserInfo_whenTokenExchangeSucceeds() {
        String tokenJson = """
                {
                  "access_token": "access-token",
                  "id_token": "id-token-value",
                  "token_type": "Bearer",
                  "expires_in": 3599
                }
                """;
        GoogleUserInfo expected = new GoogleUserInfo("sub", "user@example.com", "Cinema User");

        mockServer.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("code=auth-code"),
                        containsString("client_id=client-id.apps.googleusercontent.com"),
                        containsString("client_secret=client-secret"),
                        containsString("redirect_uri=https%3A%2F%2Fcinema-api.duckdns.org%2Fapi%2Fauth%2Fgoogle%2Fcallback"),
                        containsString("grant_type=authorization_code")
                )))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(tokenJson));

        when(googleIdTokenVerifierService.verify("id-token-value")).thenReturn(expected);

        GoogleUserInfo actual = service.exchangeCode("auth-code");

        assertThat(actual).isEqualTo(expected);
        verify(googleIdTokenVerifierService).verify("id-token-value");
        mockServer.verify();
    }

    @Test
    void exchangeCode_shouldThrowCodeExchangeFailed_whenGoogleReturns4xx() {
        String errorJson = """
                {
                  "error": "invalid_client",
                  "error_description": "Unauthorized"
                }
                """;

        mockServer.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson));

        GoogleOAuthFlowException exception = assertThrows(
                GoogleOAuthFlowException.class,
                () -> service.exchangeCode("auth-code"));

        assertThat(exception.getReason()).isEqualTo("code_exchange_failed");
        mockServer.verify();
    }

    @Test
    void exchangeCode_shouldThrowTokenInvalid_whenIdTokenMissing() {
        String tokenJsonWithoutIdToken = """
                {
                  "access_token": "access-token",
                  "token_type": "Bearer",
                  "expires_in": 3599
                }
                """;

        mockServer.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(tokenJsonWithoutIdToken));

        GoogleOAuthFlowException exception = assertThrows(
                GoogleOAuthFlowException.class,
                () -> service.exchangeCode("auth-code"));

        assertThat(exception.getReason()).isEqualTo("token_invalid");
        mockServer.verify();
    }
}
