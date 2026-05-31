package com.cinema.identity_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
import com.cinema.identity_service.dto.request.GoogleLoginRequest;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.LoginRequest;
import com.cinema.identity_service.dto.request.RegisterStaffRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import com.cinema.identity_service.entity.OtpData;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.grpc.UserGrpcClient;
import com.cinema.identity_service.mapper.UserMapper;
import com.cinema.identity_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.identity_service.repository.UserRepository;
import com.cinema.identity_service.services.google.GoogleIdTokenVerifierService;
import com.cinema.identity_service.services.google.GoogleOAuthService;
import com.cinema.identity_service.services.google.GoogleUserInfo;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
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
    private GoogleIdTokenVerifierService googleIdTokenVerifierService;
    @Mock
    private GoogleOAuthService googleOAuthService;
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
                googleIdTokenVerifierService,
                googleOAuthService,
                internalEmailDispatchService
        );

        ReflectionTestUtils.setField(service, "authCookieSecure", false);
        ReflectionTestUtils.setField(service, "authCookieSameSite", "Strict");
        ReflectionTestUtils.setField(service, "googleStateTtlMinutes", 5L);
        ReflectionTestUtils.setField(service, "googleSuccessRedirectUrl", "https://cinema-star-ten.vercel.app/auth/callback");
        ReflectionTestUtils.setField(service, "googleFailureRedirectUrl", "https://cinema-star-ten.vercel.app/login");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void login_shouldSetAccessAndRefreshCookies_andReturnActionMessage() {
        User user = buildActiveUser();
        LoginRequest request = buildLoginRequest(user.getEmail(), "plain-password");
        long accessExp = 120_000L;
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
                eq(accessExp - 60_000L),
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
        verify(valueOperations).set(anyString(), any(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void forgotPassword_shouldSetVerifyTokenCookie_usingConfiguredSecurityAttributes() {
        ReflectionTestUtils.setField(service, "authCookieSecure", true);
        ReflectionTestUtils.setField(service, "authCookieSameSite", "None");

        ForgotPasswordRequest request = new ForgotPasswordRequest();
        ReflectionTestUtils.setField(request, "email", "forgot@example.com");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);
        when(otpEncoder.encode(anyString())).thenReturn("encoded-otp");
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES))).thenReturn(true);

        MockHttpServletResponse response = new MockHttpServletResponse();

        ActionMessageResponse action = service.forgotPassword(request, response);

        assertThat(action.getMessage()).isNotBlank();
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(1);
        assertThat(setCookies.get(0)).contains("verifyToken=");
        assertThat(setCookies.get(0)).contains("Max-Age=300");
        assertThat(setCookies.get(0)).contains("HttpOnly");
        assertThat(setCookies.get(0)).contains("Secure");
        assertThat(setCookies.get(0)).contains("SameSite=None");
    }

    @Test
    void verifyOtp_success_shouldClearVerifyTokenCookie() {
        String verifyToken = "verify-token-success";
        String otpKey = "identity:otp:" + verifyToken;
        String subjectKey = "identity:otp:subject:test@example.com";

        RegisterCustomerRequest registerCustomerRequest = RegisterCustomerRequest.builder()
                .name("Test User")
                .email("test@example.com")
                .password("encoded-password")
                .dob(LocalDate.of(2000, 1, 1))
                .gender(UserEnum.Gender.MALE)
                .phone("0123456789")
                .build();

        OtpData otpData = OtpData.builder()
                .verifyToken(verifyToken)
                .subject("test@example.com")
                .otpHash("hashed-otp")
                .purpose(OtpData.OtpPurpose.REGISTER)
                .registerCustomerRequest(registerCustomerRequest)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        User userToSave = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encoded-password")
                .role(UserEnum.UserRole.CUSTOMER)
                .status(UserEnum.UserStatus.ACTIVE)
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("verifyToken", verifyToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(valueOperations.get(otpKey)).thenReturn(otpData);
        when(valueOperations.get(subjectKey)).thenReturn(verifyToken);
        when(otpEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(userMapper.toUser(registerCustomerRequest)).thenReturn(userToSave);
        when(userRepository.save(userToSave)).thenReturn(userToSave);

        ActionMessageResponse action = service.verifyOTP(new VerifyRequest("123456", null), request, response);

        assertThat(action.getMessage()).isNotBlank();
        verify(redisTemplate).delete(otpKey);
        verify(redisTemplate).delete(subjectKey);
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(v -> v.contains("verifyToken=") && v.contains("Max-Age=0"));
    }

    @Test
    void verifyOtp_tokenMismatch_shouldClearVerifyTokenCookie() {
        String cookieVerifyToken = "verify-token-cookie";
        String redisVerifyToken = "verify-token-redis";
        String otpKey = "identity:otp:" + cookieVerifyToken;
        String subjectKey = "identity:otp:subject:test@example.com";

        OtpData otpData = OtpData.builder()
                .verifyToken(cookieVerifyToken)
                .subject("test@example.com")
                .otpHash("hashed-otp")
                .purpose(OtpData.OtpPurpose.REGISTER)
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("verifyToken", cookieVerifyToken));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(valueOperations.get(otpKey)).thenReturn(otpData);
        when(valueOperations.get(subjectKey)).thenReturn(redisVerifyToken);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyOTP(new VerifyRequest("123456", null), request, response));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID);
        verify(redisTemplate).delete(otpKey);
        verify(redisTemplate).delete(subjectKey);
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).anyMatch(v -> v.contains("verifyToken=") && v.contains("Max-Age=0"));
    }

    @Test
    void googleLogin_existingGoogleUser_shouldSetAccessAndRefreshCookies() {
        User user = buildActiveUser();
        user.setProvider("GOOGLE");
        user.setProviderId("google-sub-1");

        GoogleLoginRequest request = buildGoogleLoginRequest("id-token");

        when(googleIdTokenVerifierService.verify("id-token"))
                .thenReturn(new GoogleUserInfo("google-sub-1", user.getEmail(), "Google User"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq(user), anyString())).thenReturn("google-access-token");
        when(jwtService.generateRefreshToken(eq(user), anyString())).thenReturn("google-refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(120_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(240_000L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        ActionMessageResponse action = service.googleLogin(request, response);

        assertThat(action.getMessage()).isNotBlank();
        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(v -> v.contains("accessToken=google-access-token"));
        assertThat(setCookies).anyMatch(v -> v.contains("refreshToken=google-refresh-token"));
    }

    @Test
    void googleLogin_newGoogleUser_shouldCreateProfileWithDefaults_andSetCookies() {
        GoogleLoginRequest request = buildGoogleLoginRequest("new-google-id-token");
        GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-2", "new.user@example.com", "New User");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("new.user@example.com")
                .provider("GOOGLE")
                .providerId("google-sub-2")
                .password("encoded-random")
                .role(UserEnum.UserRole.CUSTOMER)
                .status(UserEnum.UserStatus.ACTIVE)
                .build();

        when(googleIdTokenVerifierService.verify("new-google-id-token")).thenReturn(googleUser);
        when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(eq(savedUser), anyString())).thenReturn("new-google-access-token");
        when(jwtService.generateRefreshToken(eq(savedUser), anyString())).thenReturn("new-google-refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(120_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(240_000L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        ActionMessageResponse action = service.googleLogin(request, response);

        assertThat(action.getMessage()).isNotBlank();
        verify(userRepository).save(argThat(user ->
                "GOOGLE".equals(user.getProvider())
                        && "google-sub-2".equals(user.getProviderId())
                        && UserEnum.UserRole.CUSTOMER.equals(user.getRole())
                        && UserEnum.UserStatus.ACTIVE.equals(user.getStatus())
        ));
        verify(userGrpcClient).createCustomerProfile(argThat(profile ->
                savedUser.getId().equals(profile.getId())
                        && "new.user@example.com".equals(profile.getEmail())
                        && LocalDate.of(1970, 1, 1).equals(profile.getDob())
                        && UserEnum.Gender.OTHER.equals(profile.getGender())
                        && "0999999999".equals(profile.getPhone())
                        && UserEnum.UserRole.CUSTOMER.equals(profile.getRole())
        ));

        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).hasSize(2);
        assertThat(setCookies).anyMatch(v -> v.contains("accessToken=new-google-access-token"));
        assertThat(setCookies).anyMatch(v -> v.contains("refreshToken=new-google-refresh-token"));
    }

    @Test
    void googleLogin_existingLocalUser_shouldThrowEmailExisted() {
        User user = buildActiveUser();
        user.setProvider("LOCAL");

        when(googleIdTokenVerifierService.verify("google-token"))
                .thenReturn(new GoogleUserInfo("google-sub-3", user.getEmail(), "Local Conflict"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.googleLogin(buildGoogleLoginRequest("google-token"), new MockHttpServletResponse()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMAIL_EXISTED);
        verify(jwtService, never()).generateAccessToken(any(User.class), anyString());
    }

    @Test
    void googleLogin_existingGoogleUserWithDifferentProviderId_shouldThrowLoginFailed() {
        User user = buildActiveUser();
        user.setProvider("GOOGLE");
        user.setProviderId("google-sub-old");

        when(googleIdTokenVerifierService.verify("google-token"))
                .thenReturn(new GoogleUserInfo("google-sub-new", user.getEmail(), "Mismatch"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.googleLogin(buildGoogleLoginRequest("google-token"), new MockHttpServletResponse()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(jwtService, never()).generateAccessToken(any(User.class), anyString());
    }

    @Test
    void googleLogin_lockedUser_shouldThrowLoginFailed() {
        User user = buildActiveUser();
        user.setProvider("GOOGLE");
        user.setProviderId("google-sub-1");
        user.setStatus(UserEnum.UserStatus.LOCKED);

        when(googleIdTokenVerifierService.verify("google-token"))
                .thenReturn(new GoogleUserInfo("google-sub-1", user.getEmail(), "Locked"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.googleLogin(buildGoogleLoginRequest("google-token"), new MockHttpServletResponse()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(jwtService, never()).generateAccessToken(any(User.class), anyString());
    }

    @Test
    void googleLogin_invalidGoogleToken_shouldThrowLoginFailed() {
        when(googleIdTokenVerifierService.verify("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.LOGIN_FAILED));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.googleLogin(buildGoogleLoginRequest("invalid-token"), new MockHttpServletResponse()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void googleLogin_whenCreateProfileFails_shouldRollbackAndThrow() {
        GoogleLoginRequest request = buildGoogleLoginRequest("new-google-id-token");
        GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-4", "failure.user@example.com", "Failure User");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("failure.user@example.com")
                .provider("GOOGLE")
                .providerId("google-sub-4")
                .password("encoded-random")
                .role(UserEnum.UserRole.CUSTOMER)
                .status(UserEnum.UserStatus.ACTIVE)
                .build();

        when(googleIdTokenVerifierService.verify("new-google-id-token")).thenReturn(googleUser);
        when(userRepository.findByEmail("failure.user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        org.mockito.Mockito.doThrow(new RuntimeException("grpc failed"))
                .when(userGrpcClient).createCustomerProfile(any(RegisterCustomerRequest.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.googleLogin(request, new MockHttpServletResponse()));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_CREATED);
        verify(jwtService, never()).generateAccessToken(any(User.class), anyString());
    }

    @Test
    void createStaff_shouldAcceptRoleFromHeader() {
        RegisterStaffRequest requestBody = RegisterStaffRequest.builder()
                .name("Staff User")
                .email("staff@example.com")
                .password("Password@123")
                .dob(LocalDate.of(2000, 1, 1))
                .gender(UserEnum.Gender.MALE)
                .phone("0123456789")
                .build();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_MANAGER);

        UUID staffId = UUID.randomUUID();
        User mappedStaff = User.builder()
                .id(staffId)
                .role(UserEnum.UserRole.STAFF)
                .build();
        User savedStaff = User.builder()
                .id(staffId)
                .role(UserEnum.UserRole.STAFF)
                .build();

        when(userRepository.existsByEmail(requestBody.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(requestBody.getPassword())).thenReturn("encoded-password");
        when(userMapper.toUser(any(RegisterStaffRequest.class))).thenReturn(mappedStaff);
        when(userRepository.save(any(User.class))).thenReturn(savedStaff);

        ActionMessageResponse action = service.createStaff(requestBody, request);

        assertThat(action.getMessage()).isNotBlank();
        verify(userGrpcClient).createStaffProfile(any(RegisterStaffRequest.class));
    }

    @Test
    void googleAuthorize_shouldStoreStateAndRedirectToGoogle() {
        when(googleOAuthService.buildAuthorizationUrl(anyString())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?mock=1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleAuthorize(response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth?mock=1");

        var stateCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(googleOAuthService).buildAuthorizationUrl(stateCaptor.capture());
        String state = stateCaptor.getValue();
        verify(valueOperations).set(
                eq("identity:oauth:google:state:" + state),
                eq(state),
                eq(5L),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void googleCallback_existingGoogleUser_shouldSetCookies_andRedirectSuccess() {
        String state = "state-existing-google";
        String stateKey = "identity:oauth:google:state:" + state;
        User user = buildActiveUser();
        user.setProvider("GOOGLE");
        user.setProviderId("google-sub-1");

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code")).thenReturn(new GoogleUserInfo("google-sub-1", user.getEmail(), "Google User"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(eq(user), anyString())).thenReturn("google-access-token");
        when(jwtService.generateRefreshToken(eq(user), anyString())).thenReturn("google-refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(120_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(240_000L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleSuccessRedirect());
        verify(redisTemplate).delete(stateKey);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2);
    }

    @Test
    void googleCallback_invalidState_shouldRedirectWithStateInvalid() {
        String state = "state-missing";
        String stateKey = "identity:oauth:google:state:" + state;
        when(valueOperations.get(stateKey)).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleFailureRedirect("state_invalid"));
        verify(googleOAuthService, never()).exchangeCode(anyString());
    }

    @Test
    void googleCallback_newGoogleUser_shouldCreateProfileAndRedirectSuccess() {
        String state = "state-new-google";
        String stateKey = "identity:oauth:google:state:" + state;
        GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-2", "new.user@example.com", "New User");
        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("new.user@example.com")
                .provider("GOOGLE")
                .providerId("google-sub-2")
                .password("encoded-random")
                .role(UserEnum.UserRole.CUSTOMER)
                .status(UserEnum.UserStatus.ACTIVE)
                .build();

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code")).thenReturn(googleUser);
        when(userRepository.findByEmail("new.user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(eq(savedUser), anyString())).thenReturn("new-google-access-token");
        when(jwtService.generateRefreshToken(eq(savedUser), anyString())).thenReturn("new-google-refresh-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(120_000L);
        when(jwtService.getRefreshTokenExpiration()).thenReturn(240_000L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleSuccessRedirect());
        verify(userGrpcClient).createCustomerProfile(argThat(profile ->
                savedUser.getId().equals(profile.getId())
                        && "new.user@example.com".equals(profile.getEmail())
                        && LocalDate.of(1970, 1, 1).equals(profile.getDob())
                        && UserEnum.Gender.OTHER.equals(profile.getGender())
                        && "0999999999".equals(profile.getPhone())
                        && UserEnum.UserRole.CUSTOMER.equals(profile.getRole())
        ));
    }

    @Test
    void googleCallback_existingLocalUser_shouldRedirectWithEmailConflict() {
        String state = "state-local-conflict";
        String stateKey = "identity:oauth:google:state:" + state;
        User user = buildActiveUser();
        user.setProvider("LOCAL");

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code")).thenReturn(new GoogleUserInfo("google-sub-3", user.getEmail(), "Local Conflict"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleFailureRedirect("email_conflict"));
        verify(jwtService, never()).generateAccessToken(any(User.class), anyString());
    }

    @Test
    void googleCallback_existingGoogleUserWithDifferentProviderId_shouldRedirectWithLoginFailed() {
        String state = "state-provider-mismatch";
        String stateKey = "identity:oauth:google:state:" + state;
        User user = buildActiveUser();
        user.setProvider("GOOGLE");
        user.setProviderId("google-sub-old");

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code")).thenReturn(new GoogleUserInfo("google-sub-new", user.getEmail(), "Mismatch"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleFailureRedirect("login_failed"));
    }

    @Test
    void googleCallback_lockedUser_shouldRedirectWithLoginFailed() {
        String state = "state-locked";
        String stateKey = "identity:oauth:google:state:" + state;
        User user = buildActiveUser();
        user.setProvider("GOOGLE");
        user.setProviderId("google-sub-1");
        user.setStatus(UserEnum.UserStatus.LOCKED);

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code")).thenReturn(new GoogleUserInfo("google-sub-1", user.getEmail(), "Locked"));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleFailureRedirect("login_failed"));
    }

    @Test
    void googleCallback_invalidGoogleToken_shouldRedirectWithTokenInvalid() {
        String state = "state-token-invalid";
        String stateKey = "identity:oauth:google:state:" + state;

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code"))
                .thenThrow(new com.cinema.identity_service.services.google.GoogleOAuthFlowException("token_invalid"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleFailureRedirect("token_invalid"));
    }

    @Test
    void googleCallback_codeExchangeFail_shouldRedirectWithCodeExchangeFailed() {
        String state = "state-exchange-fail";
        String stateKey = "identity:oauth:google:state:" + state;

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code"))
                .thenThrow(new com.cinema.identity_service.services.google.GoogleOAuthFlowException("code_exchange_failed"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleFailureRedirect("code_exchange_failed"));
    }

    @Test
    void googleCallback_whenCreateProfileFails_shouldRedirectWithProfileCreationFailed() {
        String state = "state-create-fail";
        String stateKey = "identity:oauth:google:state:" + state;
        GoogleUserInfo googleUser = new GoogleUserInfo("google-sub-4", "failure.user@example.com", "Failure User");
        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email("failure.user@example.com")
                .provider("GOOGLE")
                .providerId("google-sub-4")
                .password("encoded-random")
                .role(UserEnum.UserRole.CUSTOMER)
                .status(UserEnum.UserStatus.ACTIVE)
                .build();

        when(valueOperations.get(stateKey)).thenReturn(state);
        when(googleOAuthService.exchangeCode("auth-code")).thenReturn(googleUser);
        when(userRepository.findByEmail("failure.user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-random");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        org.mockito.Mockito.doThrow(new RuntimeException("grpc failed"))
                .when(userGrpcClient).createCustomerProfile(any(RegisterCustomerRequest.class));

        MockHttpServletResponse response = new MockHttpServletResponse();

        service.googleCallback("auth-code", state, null, response);

        assertThat(response.getRedirectedUrl()).isEqualTo(googleFailureRedirect("profile_creation_failed"));
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

    private GoogleLoginRequest buildGoogleLoginRequest(String idToken) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        ReflectionTestUtils.setField(request, "idToken", idToken);
        return request;
    }

    private String googleSuccessRedirect() {
        return "https://cinema-star-ten.vercel.app/auth/callback?oauth=google&status=success";
    }

    private String googleFailureRedirect(String reason) {
        return "https://cinema-star-ten.vercel.app/login?oauth=google&status=error&code=" + reason;
    }
}
