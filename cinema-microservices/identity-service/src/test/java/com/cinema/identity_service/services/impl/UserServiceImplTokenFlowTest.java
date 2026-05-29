package com.cinema.identity_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.LoginRequest;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.grpc.UserGrpcClient;
import com.cinema.identity_service.mapper.UserMapper;
import com.cinema.identity_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.identity_service.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTokenFlowTest {

    @Mock
    private JwtServiceImpl jwtService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordEncoder otpEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private SetOperations<String, Object> setOperations;
    @Mock
    private UserGrpcClient userGrpcClient;
    @Mock
    private InternalEmailDispatchService internalEmailDispatchService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(
                jwtService,
                userRepository,
                passwordEncoder,
                otpEncoder,
                userMapper,
                redisTemplate,
                userGrpcClient,
                internalEmailDispatchService
        );

        ReflectionTestUtils.setField(service, "authCookieSecure", false);
        ReflectionTestUtils.setField(service, "authCookieSameSite", "Strict");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void login_shouldSetAccessAndRefreshCookies_andReturnActionMessage() {
        User user = buildActiveUser();
        LoginRequest request = buildLoginRequest(user.getEmail(), "plain-password");
        long accessExp = 60_000L;
        long refreshExp = 120_000L;

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("plain-password", user.getPassword())).thenReturn(true);
        when(jwtService.generateAccessToken(eq(user), anyString())).thenReturn("access-token-value");
        when(jwtService.generateRefreshToken(eq(user), anyString())).thenReturn("refresh-token-value");
        when(jwtService.getAccessTokenExpiration()).thenReturn(accessExp);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(refreshExp);

        MockHttpServletResponse response = new MockHttpServletResponse();

        ActionMessageResponse action = service.login(request, response);

        assertThat(action.getMessage()).isNotBlank();
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(v -> v.contains("accessToken=access-token-value"));
        assertThat(setCookies).anyMatch(v -> v.contains("refreshToken=refresh-token-value"));

        verify(valueOperations).set(
                argThat(key -> key.startsWith("identity:token:access:")),
                eq(user.getId().toString()),
                eq(accessExp),
                eq(TimeUnit.MILLISECONDS)
        );
        verify(valueOperations).set(
                argThat(key -> key.startsWith("identity:token:refresh:")),
                eq(user.getId().toString()),
                eq(refreshExp),
                eq(TimeUnit.MILLISECONDS)
        );
        verify(setOperations).add(eq("identity:user_tokens:" + user.getId()), anyString());
    }

    @Test
    void logout_withoutToken_shouldStillClearAuthCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ActionMessageResponse action = service.logout(request, response);

        assertThat(action.getMessage()).isNotBlank();
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(v -> v.contains("accessToken=") && v.contains("Max-Age=0"));
        assertThat(setCookies).anyMatch(v -> v.contains("refreshToken=") && v.contains("Max-Age=0"));
        verify(jwtService, never()).extractUserId(anyString());
    }

    @Test
    void refreshToken_missingCookie_shouldThrowAndClearAuthCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refreshToken(request, response));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING);
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(v -> v.contains("accessToken=") && v.contains("Max-Age=0"));
        assertThat(setCookies).anyMatch(v -> v.contains("refreshToken=") && v.contains("Max-Age=0"));
    }

    @Test
    void refreshToken_validCookie_shouldRotateAndSetNewCookies() {
        UUID userId = UUID.randomUUID();
        User user = buildActiveUser();
        user.setId(userId);

        String oldTokenId = "old-token-id";
        String oldRefreshToken = "old-refresh-token";
        long refreshRemaining = 90_000L;
        long accessExp = 30_000L;
        String refreshKey = "identity:token:refresh:" + oldTokenId;
        String accessKey = "identity:token:access:" + oldTokenId;

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", oldRefreshToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractTokenId(oldRefreshToken)).thenReturn(oldTokenId);
        when(jwtService.extractTokenType(oldRefreshToken)).thenReturn("refresh");
        when(jwtService.extractUserId(oldRefreshToken)).thenReturn(userId);
        when(jwtService.extractExpirationMillis(oldRefreshToken)).thenReturn(System.currentTimeMillis() + refreshRemaining);
        when(valueOperations.get(refreshKey)).thenReturn(userId.toString());
        when(redisTemplate.hasKey(accessKey)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq(user), anyString())).thenReturn("new-access-token");
        when(jwtService.generateRefreshTokenWithExp(eq(user), anyString(), anyLong())).thenReturn("new-refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(accessExp);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(7 * 24 * 60 * 60 * 1000L);

        ActionMessageResponse action = service.refreshToken(request, response);

        assertThat(action.getMessage()).isNotBlank();
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(v -> v.contains("accessToken=new-access-token"));
        assertThat(setCookies).anyMatch(v -> v.contains("refreshToken=new-refresh-token"));

        verify(redisTemplate).delete(refreshKey);
        verify(valueOperations, times(2)).set(
                argThat(key -> key.startsWith("identity:token:")),
                eq(userId.toString()),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void refreshToken_whenAccessTokenStillExists_shouldThrowAndClearCookies() {
        UUID userId = UUID.randomUUID();
        String oldTokenId = "old-token-id";
        String oldRefreshToken = "old-refresh-token";
        String refreshKey = "identity:token:refresh:" + oldTokenId;
        String accessKey = "identity:token:access:" + oldTokenId;
        String userTokensKey = "identity:user_tokens:" + userId;

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("refreshToken", oldRefreshToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractTokenId(oldRefreshToken)).thenReturn(oldTokenId);
        when(jwtService.extractTokenType(oldRefreshToken)).thenReturn("refresh");
        when(jwtService.extractUserId(oldRefreshToken)).thenReturn(userId);
        when(jwtService.extractExpirationMillis(oldRefreshToken)).thenReturn(System.currentTimeMillis() + 60_000L);
        when(valueOperations.get(refreshKey)).thenReturn(userId.toString());
        when(redisTemplate.hasKey(accessKey)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.refreshToken(request, response));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING);
        verify(redisTemplate).delete(refreshKey);
        verify(redisTemplate).delete(accessKey);
        verify(setOperations).remove(userTokensKey, oldTokenId);

        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(v -> v.contains("accessToken=") && v.contains("Max-Age=0"));
        assertThat(setCookies).anyMatch(v -> v.contains("refreshToken=") && v.contains("Max-Age=0"));
    }

    @Test
    void registerCustomer_shouldSetVerifyTokenCookie() {
        RegisterCustomerRequest request = RegisterCustomerRequest.builder()
                .name("Test User")
                .email("test@example.com")
                .password("Password@123")
                .dob(LocalDate.of(2000, 1, 1))
                .gender(UserEnum.Gender.MALE)
                .phone("0123456789")
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(otpEncoder.encode(anyString())).thenReturn("encoded-otp");
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES))).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();

        ActionMessageResponse action = service.registerCustomer(request, response);

        assertThat(action.getMessage()).isNotBlank();
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        assertThat(setCookies.get(0)).contains("verifyToken=");
        assertThat(setCookies.get(0)).contains("Max-Age=300");
        verify(redisTemplate).opsForValue();
        verify(valueOperations).set(anyString(), any(), eq(5L), eq(TimeUnit.MINUTES));
    }

    private User buildActiveUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("tester@cinema.local")
                .password("encoded-password")
                .role(UserEnum.UserRole.CUSTOMER)
                .status(UserEnum.UserStatus.ACTIVE)
                .build();
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }
}
