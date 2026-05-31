package com.cinema.identity_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.dto.request.SendEmailRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import com.cinema.identity_service.dto.request.ChangePasswordRequest;
import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
import com.cinema.identity_service.dto.request.GoogleLoginRequest;
import com.cinema.identity_service.dto.request.LoginRequest;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.RegisterManagerRequest;
import com.cinema.identity_service.dto.request.RegisterStaffRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import com.cinema.identity_service.entity.OtpData;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.grpc.UserGrpcClient;
import com.cinema.identity_service.mapper.UserMapper;
import com.cinema.identity_service.messaging.publisher.InternalEmailDispatchService;
import com.cinema.identity_service.repository.UserRepository;
import com.cinema.identity_service.services.UserService;
import com.cinema.identity_service.services.internal.IdentityAccountInternalService;
import com.cinema.identity_service.services.google.GoogleIdTokenVerifierService;
import com.cinema.identity_service.services.google.GoogleOAuthFlowException;
import com.cinema.identity_service.services.google.GoogleOAuthService;
import com.cinema.identity_service.services.google.GoogleUserInfo;
import com.cinema.identity_service.utils.OTPGenerator;
import com.cinema.identity_service.utils.VerifyTokenUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.cinema.exception.ErrorCode.*;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {
    final JwtServiceImpl jwtServiceImpl;
    final UserRepository userRepository;
    final PasswordEncoder passwordEncoder;
    @Qualifier("otpEncoder")
    final PasswordEncoder otpEncoder;
    final UserMapper userMapper;
    final RedisTemplate<String, Object> redisTemplate;
    final UserGrpcClient userGrpcClient;
    final IdentityAccountInternalService identityAccountInternalService;
    final GoogleIdTokenVerifierService googleIdTokenVerifierService;
    final GoogleOAuthService googleOAuthService;
    final InternalEmailDispatchService internalEmailDispatchService;
    @Value("${app.auth.cookie.secure:true}")
    boolean authCookieSecure;
    @Value("${app.auth.cookie.same-site:None}")
    String authCookieSameSite;
    @Value("${app.auth.google.state-ttl-minutes:5}")
    long googleStateTtlMinutes;
    @Value("${app.auth.google.success-redirect-url:https://cinema-star-ten.vercel.app/auth/callback}")
    String googleSuccessRedirectUrl;
    @Value("${app.auth.google.failure-redirect-url:https://cinema-star-ten.vercel.app/login}")
    String googleFailureRedirectUrl;

    static String VerifyToken = "verifyToken";
    static String AccessToken = "accessToken";
    static String RefreshToken = "refreshToken";

    static String OTP_PREFIX = "identity:otp:";
    static String OTP_SUBJECT_PREFIX = "identity:otp:subject:";
    static String ACCESS_TOKEN_PREFIX = "identity:token:access:";
    static String REFRESH_TOKEN_PREFIX = "identity:token:refresh:";
    static String USER_TOKENS_PREFIX = "identity:user_tokens:";
    private static final String GOOGLE_STATE_PREFIX = "identity:oauth:google:state:";
    private static final long TOKEN_EXPIRY_BUFFER = 60_000L;
    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final LocalDate DEFAULT_GOOGLE_DOB = LocalDate.of(1970, 1, 1);
    private static final UserEnum.Gender DEFAULT_GOOGLE_GENDER = UserEnum.Gender.OTHER;
    private static final String DEFAULT_GOOGLE_PHONE = "0999999999";
    private static final String GOOGLE_OAUTH_PARAM = "oauth";
    private static final String GOOGLE_OAUTH_PARAM_VALUE = "google";
    private static final String GOOGLE_STATUS_PARAM = "status";
    private static final String GOOGLE_STATUS_SUCCESS = "success";
    private static final String GOOGLE_STATUS_ERROR = "error";
    private static final String GOOGLE_ERROR_REASON_STATE_INVALID = "state_invalid";
    private static final String GOOGLE_ERROR_REASON_CODE_EXCHANGE_FAILED = "code_exchange_failed";
    private static final String GOOGLE_ERROR_REASON_TOKEN_INVALID = "token_invalid";
    private static final String GOOGLE_ERROR_REASON_EMAIL_CONFLICT = "email_conflict";
    private static final String GOOGLE_ERROR_REASON_LOGIN_FAILED = "login_failed";
    private static final String GOOGLE_ERROR_REASON_PROFILE_CREATION_FAILED = "profile_creation_failed";
    private static final String GOOGLE_ERROR_REASON_OAUTH_DENIED = "oauth_denied";

    static Pattern STRONG_PASSWORD_PATTERN = Pattern
            .compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,32}$");
    static int MAX_VERIFY_ATTEMPTS = 3;
    static int MAX_SEND_COUNT = 4;

    // Register customer account: create OTP payload, cache verification state, and
    // queue OTP email.
    @Override
    public ActionMessageResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest,
                                                  HttpServletResponse response) {
        if (userRepository.existsByEmail(registerCustomerRequest.getEmail())) {
            throw new BusinessException(EMAIL_EXISTED);
        }

        RegisterCustomerRequest customerRequest = RegisterCustomerRequest.builder()
                .name(registerCustomerRequest.getName())
                .email(registerCustomerRequest.getEmail())
                .password(passwordEncoder.encode(registerCustomerRequest.getPassword()))
                .dob(registerCustomerRequest.getDob())
                .phone(registerCustomerRequest.getPhone())
                .gender(registerCustomerRequest.getGender())
                .build();

        String otp = OTPGenerator.generateOTP();
        String verifyToken = VerifyTokenUtils.generate();
        OtpData otpData = OtpData.builder()
                .verifyToken(verifyToken)
                .subject(customerRequest.getEmail())
                .purpose(OtpData.OtpPurpose.REGISTER)
                .registerCustomerRequest(customerRequest)
                .otpHash(otpEncoder.encode(otp))
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .lastSentAt(LocalDateTime.now())
                .build();

        String otpKey = OTP_PREFIX + verifyToken;
        String subjectKey = OTP_SUBJECT_PREFIX + customerRequest.getEmail();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(subjectKey, verifyToken, 5, TimeUnit.MINUTES);

        if (!Boolean.TRUE.equals(success)) {
            throw new BusinessException(OTP_ALREADY_SENT);
        }
        log.warn("otp generate :{}", otp);
        redisTemplate.opsForValue().set(otpKey, otpData, 5, TimeUnit.MINUTES);
        setTokenCookie(response, VerifyToken, verifyToken, TimeUnit.MINUTES.toMillis(5));
        internalEmailDispatchService.sendAsync(new SendEmailRequest(
                registerCustomerRequest.getEmail(),
                "Đăng ký tài khoản thành công",
                "Chào mừng bạn đến với CinemaStar!",
                "Mã OTP của bạn là: " + otp + "</p>"));
        log.info("OTP email dispatch queued for registration: email={}", registerCustomerRequest.getEmail());
        return ActionMessageResponse.builder()
                .message("Mã OTP đã được gửi đến email của bạn")
                .build();
    }

    // Create manager account in identity-service, then provision manager profile in
    // user-service.
    @Override
    public ActionMessageResponse createManager(RegisterManagerRequest registerManagerRequest,
                                               HttpServletRequest request) {
        // Kiem tra email da ton tai
        if (userRepository.existsByEmail(registerManagerRequest.getEmail())) {
            throw new BusinessException(EMAIL_EXISTED);
        }

        // Tao manager request voi password da ma hoa
        RegisterManagerRequest managerRequest = RegisterManagerRequest.builder()
                .name(registerManagerRequest.getName())
                .email(registerManagerRequest.getEmail())
                .password(passwordEncoder.encode(registerManagerRequest.getPassword()))
                .dob(registerManagerRequest.getDob())
                .phone(registerManagerRequest.getPhone())
                .gender(registerManagerRequest.getGender())
                .build();

        // Luu user vao identity-service database
        User newManager = userMapper.toUser(managerRequest);
        User savedManager = userRepository.save(newManager);

        try {
            // Goi user-service de tao profile manager
            RegisterManagerRequest userServiceRequest = RegisterManagerRequest.builder()
                    .id(savedManager.getId())
                    .name(managerRequest.getName())
                    .email(managerRequest.getEmail())
                    .dob(managerRequest.getDob())
                    .gender(managerRequest.getGender())
                    .phone(managerRequest.getPhone())
                    .role(savedManager.getRole())
                    .bankCode(managerRequest.getBankCode())
                    .accountNumber(managerRequest.getAccountNumber())
                    .accountName(managerRequest.getAccountName())
                    .build();

            userGrpcClient.createManagerProfile(userServiceRequest);
            log.info("Manager created in user-service: managerId={}, email={}", savedManager.getId(),
                    managerRequest.getEmail());

        } catch (BusinessException e) {
            log.error("Failed to create manager in user-service: email={}", managerRequest.getEmail(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create manager in user-service: email={}", managerRequest.getEmail(), e);
            throw new BusinessException(ErrorCode.NOT_CREATED);
        }
        // Gui email chao mung cho manager
        internalEmailDispatchService.sendAsync(new SendEmailRequest(
                registerManagerRequest.getEmail(),
                "Chào mừng bạn trở thành Manager",
                "Tài khoản Manager đã được tạo thành công.",
                "Chúc mừng bạn đã trở thành Manager tại CinemaStar!"));
        log.info("Welcome email dispatch queued for manager: email={}", registerManagerRequest.getEmail());
        return ActionMessageResponse.builder()
                .message("Tạo manager thành công")
                .build();
    }

    // Create staff account in identity-service, then provision staff profile in
    // user-service.
    @Override
    public ActionMessageResponse createStaff(RegisterStaffRequest registerStaffRequest,
                                             HttpServletRequest request) {
        RequestAuthUtils.requireAnyRole(request, log, "createStaff",
                HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER);

        // Kiem tra email da ton tai
        if (userRepository.existsByEmail(registerStaffRequest.getEmail())) {
            throw new BusinessException(EMAIL_EXISTED);
        }

        // Tao staff request voi password da ma hoa
        RegisterStaffRequest staffRequest = RegisterStaffRequest.builder()
                .name(registerStaffRequest.getName())
                .email(registerStaffRequest.getEmail())
                .password(passwordEncoder.encode(registerStaffRequest.getPassword()))
                .dob(registerStaffRequest.getDob())
                .phone(registerStaffRequest.getPhone())
                .gender(registerStaffRequest.getGender())
                .build();

        // Luu user vao identity-service database
        User newStaff = userMapper.toUser(staffRequest);
        User savedStaff = userRepository.save(newStaff);

        try {
            // Goi user-service de tao profile staff
            RegisterStaffRequest userServiceRequest = RegisterStaffRequest.builder()
                    .id(savedStaff.getId())
                    .name(staffRequest.getName())
                    .email(staffRequest.getEmail())
                    .dob(staffRequest.getDob())
                    .gender(staffRequest.getGender())
                    .phone(staffRequest.getPhone())
                    .role(savedStaff.getRole())
                    .bankCode(staffRequest.getBankCode())
                    .accountNumber(staffRequest.getAccountNumber())
                    .accountName(staffRequest.getAccountName())
                    .build();

            userGrpcClient.createStaffProfile(userServiceRequest);
            log.info("Staff created in user-service: staffId={}, email={}", savedStaff.getId(),
                    staffRequest.getEmail());

        } catch (BusinessException e) {
            log.error("Failed to create staff in user-service: email={}", staffRequest.getEmail(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create staff in user-service: email={}", staffRequest.getEmail(), e);
            throw new BusinessException(ErrorCode.NOT_CREATED);
        }

        internalEmailDispatchService.sendAsync(new SendEmailRequest(
                registerStaffRequest.getEmail(),
                "Chào mừng bạn trở thành Staff",
                "Tài khoản Staff đã được tạo thành công.",
                "Chúc mừng bạn đã trở thành Staff tại CinemaStar!"));
        log.info("Welcome email dispatch queued for staff: email={}", registerStaffRequest.getEmail());

        return ActionMessageResponse.builder()
                .message("Tạo staff thành công")
                .build();
    }

    // Resend OTP with anti-abuse checks (token ownership, expiry window, and resend
    // limit).
    @Override
    public ActionMessageResponse resendOTP(HttpServletRequest request) {
        String cookieVerifyToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (VerifyToken.equals(cookie.getName())) {
                    cookieVerifyToken = cookie.getValue();
                    break;
                }
            }
        }

        if (cookieVerifyToken == null) {
            throw new BusinessException(ErrorCode.VERIFY_TOKEN_MISSING);
        }

        String otpKey = OTP_PREFIX + cookieVerifyToken;
        OtpData otpData = getOtpData(otpKey);
        if (otpData == null) {
            throw new BusinessException(OTP_INVALID);
        }

        String subjectKey = OTP_SUBJECT_PREFIX + otpData.getSubject();
        String redisVerifyToken = (String) redisTemplate.opsForValue().get(subjectKey);

        if (redisVerifyToken == null) {
            redisTemplate.delete(otpKey);
            throw new BusinessException(OTP_INVALID);
        }

        if (!cookieVerifyToken.equals(redisVerifyToken)) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(subjectKey);
            throw new BusinessException(OTP_INVALID);
        }

        if (otpData.getExpiredAt().isBefore(LocalDateTime.now())) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(subjectKey);
            throw new BusinessException(OTP_INVALID);
        }

        if (otpData.getSendCount() >= MAX_SEND_COUNT) {
            throw new BusinessException(OTP_SEND_LIMIT);
        }

        String newOtp = OTPGenerator.generateOTP();
        log.warn("otp resend generate :{}", newOtp);

        otpData.setOtpHash(otpEncoder.encode(newOtp));
        otpData.setExpiredAt(LocalDateTime.now().plusMinutes(5));
        otpData.setLastSentAt(LocalDateTime.now());
        otpData.setSendCount(otpData.getSendCount() + 1);
        otpData.setVerifyAttempts(otpData.getVerifyAttempts());
        redisTemplate.opsForValue().set(otpKey, otpData, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(OTP_SUBJECT_PREFIX + otpData.getSubject(), cookieVerifyToken, 5,
                TimeUnit.MINUTES);

        internalEmailDispatchService.sendAsync(new SendEmailRequest(
                otpData.getSubject(),
                "Gửi lại mã OTP",
                "Bạn vừa yêu cầu gửi lại mã OTP.",
                "Mã OTP của bạn là: " + newOtp + "</p>"));
        log.info("OTP email redispatch queued: email={}", otpData.getSubject());
        return ActionMessageResponse.builder()
                .message("Gửi lại mã OTP thành công")
                .build();
    }

    // Start forgot-password flow by issuing OTP and storing request context in
    // Redis.
    @Override
    public ActionMessageResponse forgotPassword(ForgotPasswordRequest forgotPasswordRequest,
                                                HttpServletResponse response) {
        if (!userRepository.existsByEmail(forgotPasswordRequest.getEmail())) {
            throw new BusinessException(USER_NOT_FOUND);
        }
        String otp = OTPGenerator.generateOTP();
        String verifyToken = VerifyTokenUtils.generate();
        OtpData otpData = OtpData.builder()
                .verifyToken(verifyToken)
                .subject(forgotPasswordRequest.getEmail())
                .purpose(OtpData.OtpPurpose.FORGOT_PASSWORD)
                .forgotPasswordRequest(forgotPasswordRequest)
                .otpHash(otpEncoder.encode(otp))
                .expiredAt(LocalDateTime.now().plusMinutes(5))
                .lastSentAt(LocalDateTime.now())
                .build();

        String otpKey = OTP_PREFIX + verifyToken;
        String subjectKey = OTP_SUBJECT_PREFIX + forgotPasswordRequest.getEmail();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(subjectKey, verifyToken, 5, TimeUnit.MINUTES);

        if (!Boolean.TRUE.equals(success)) {
            throw new BusinessException(OTP_ALREADY_SENT);
        }

        redisTemplate.opsForValue().set(otpKey, otpData, 5, TimeUnit.MINUTES);
        setTokenCookie(response, VerifyToken, verifyToken, TimeUnit.MINUTES.toMillis(5));
        log.warn("otp forgot generate :{}", otp);
        internalEmailDispatchService.sendAsync(new SendEmailRequest(
                forgotPasswordRequest.getEmail(),
                "Quên mật khẩu - OTP",
                "Bạn vừa yêu cầu lấy lại mật khẩu.",
                "Mã OTP của bạn là: " + otp + "</p>"));
        log.info("OTP email dispatch queued for forgot password: email={}", forgotPasswordRequest.getEmail());

        return ActionMessageResponse.builder()
                .message("Mã OTP đã được gửi đến email của bạn")
                .build();
    }

    // Change password for authenticated user after validating old/new password
    // constraints.
    @Override
    public ActionMessageResponse changePassword(ChangePasswordRequest changePasswordRequest,
                                                HttpServletRequest request) {
        UUID userUUID = RequestAuthUtils.requireUserId(request, ErrorCode.UNAUTHORIZED);
        identityAccountInternalService.changePassword(
                userUUID,
                changePasswordRequest.getOldPassword(),
                changePasswordRequest.getNewPassword());
        log.info("Password changed for user: {}", userUUID);
        return ActionMessageResponse.builder()
                .message("Đổi mật khẩu thành công")
                .build();
    }

    // Verify OTP and execute follow-up action (register account or reset password).
    @Override
    public ActionMessageResponse verifyOTP(VerifyRequest verifyRequest, HttpServletRequest request,
                                           HttpServletResponse response) {
        String cookieVerifyToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (VerifyToken.equals(cookie.getName())) {
                    cookieVerifyToken = cookie.getValue();
                    break;
                }
            }
        }

        if (cookieVerifyToken == null) {
            throw new BusinessException(ErrorCode.VERIFY_TOKEN_MISSING);
        }

        String otpKey = OTP_PREFIX + cookieVerifyToken;
        OtpData otpData = getOtpData(otpKey);
        if (otpData == null) {
            clearTokenCookie(response, VerifyToken);
            throw new BusinessException(OTP_INVALID);
        }

        String subjectKey = OTP_SUBJECT_PREFIX + otpData.getSubject();
        String redisVerifyToken = (String) redisTemplate.opsForValue().get(subjectKey);

        if (redisVerifyToken == null) {
            redisTemplate.delete(otpKey);
            clearTokenCookie(response, VerifyToken);
            throw new BusinessException(OTP_INVALID);
        }

        if (!cookieVerifyToken.equals(redisVerifyToken)) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(subjectKey);
            clearTokenCookie(response, VerifyToken);
            throw new BusinessException(OTP_INVALID);
        }

        if (otpData.getExpiredAt().isBefore(LocalDateTime.now())) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(subjectKey);
            clearTokenCookie(response, VerifyToken);
            throw new BusinessException(OTP_INVALID);
        }

        if (otpData.getVerifyAttempts() >= MAX_VERIFY_ATTEMPTS) {
            throw new BusinessException(OTP_VERIFY_LIMIT);
        }

        if (!otpEncoder.matches(verifyRequest.getOtp(), otpData.getOtpHash())) {
            otpData.setVerifyAttempts(otpData.getVerifyAttempts() + 1);
            redisTemplate.opsForValue().set(otpKey, otpData,
                    Duration.between(LocalDateTime.now(), otpData.getExpiredAt()));
            throw new BusinessException(OTP_INVALID);
        }

        log.info("OTP purpose = {}", otpData.getPurpose());
        switch (otpData.getPurpose()) {
            case REGISTER:
                RegisterCustomerRequest registerCustomerRequest = otpData.getRegisterCustomerRequest();
                // Save user to identity-service database
                User user_register = userMapper.toUser(registerCustomerRequest);
                User user = userRepository.save(user_register);

                try {
                    RegisterCustomerRequest userServiceRequest = RegisterCustomerRequest.builder()
                            .id(user.getId())
                            .name(registerCustomerRequest.getName())
                            .email(registerCustomerRequest.getEmail())
                            .dob(registerCustomerRequest.getDob())
                            .gender(registerCustomerRequest.getGender())
                            .phone(registerCustomerRequest.getPhone())
                            .role(user.getRole())
                            .build();

                    userGrpcClient.createCustomerProfile(userServiceRequest);
                    log.info("User created in user-service: userId={}, email={}", user.getId(),
                            registerCustomerRequest.getEmail());

                } catch (BusinessException e) {
                    log.error(
                            "Failed to create user in user-service: email={}",
                            registerCustomerRequest.getEmail(), e);
                    throw e;
                } catch (Exception e) {
                    log.error(
                            "Failed to create user in user-service: email={}",
                            registerCustomerRequest.getEmail(), e);
                    throw new BusinessException(ErrorCode.NOT_CREATED);
                }
                break;

            case FORGOT_PASSWORD:
                if (verifyRequest.getPassword() == null) {
                    otpData.setVerifyAttempts(otpData.getVerifyAttempts() + 1);
                    redisTemplate.opsForValue().set(otpKey, otpData,
                            Duration.between(LocalDateTime.now(), otpData.getExpiredAt()));
                    throw new BusinessException(PASSWORD_REQUIRED);
                }

                if (!STRONG_PASSWORD_PATTERN.matcher(verifyRequest.getPassword()).matches()) {
                    otpData.setVerifyAttempts(otpData.getVerifyAttempts() + 1);
                    redisTemplate.opsForValue().set(otpKey, otpData,
                            Duration.between(LocalDateTime.now(), otpData.getExpiredAt()));
                    throw new BusinessException(PASSWORD_INVALID);
                }
                User user_forgotPassword = userRepository.findByEmail(otpData.getSubject())
                        .orElseThrow(() -> {
                            otpData.setVerifyAttempts(otpData.getVerifyAttempts() + 1);
                            redisTemplate.opsForValue().set(
                                    otpKey,
                                    otpData,
                                    Duration.between(LocalDateTime.now(), otpData.getExpiredAt()));
                            return new BusinessException(USER_NOT_FOUND);
                        });
                user_forgotPassword.setPassword(passwordEncoder.encode(verifyRequest.getPassword()));
                userRepository.save(user_forgotPassword);
                break;
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(OTP_SUBJECT_PREFIX + otpData.getSubject());
        clearTokenCookie(response, VerifyToken);

        return ActionMessageResponse.builder()
                .message("Xác thực OTP thành công")
                .build();
    }

    // Authenticate user, issue access/refresh tokens, and persist token state in
    // Redis.
    @Override
    public ActionMessageResponse login(LoginRequest loginRequest, HttpServletResponse response) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new BusinessException(LOGIN_FAILED));
        if (isLockedOrDeleted(user)) {
            log.warn("Tài khoản đã bị khóa: {}", user.getId());
            throw new BusinessException(LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            log.warn("Mật khẩu không khớp: {}", user.getId());
            throw new BusinessException(LOGIN_FAILED);
        }

        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtServiceImpl.generateAccessToken(user, tokenId);
        String refreshToken = jwtServiceImpl.generateRefreshToken(user, tokenId);

        String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
        String refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenId;


        //Trừ thêm 1 biến buffer để không xảy ra trường hợp dưới local hết hạn mà trên redis vẫn còn hạn buffer = 60s
        redisTemplate.opsForValue().set(accessTokenKey, user.getId().toString(), jwtServiceImpl.getAccessTokenExpiration() - TOKEN_EXPIRY_BUFFER,
                TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(refreshTokenKey, user.getId().toString(), jwtServiceImpl.getRefreshTokenExpiration(),
                TimeUnit.MILLISECONDS);

        String userTokensKey = USER_TOKENS_PREFIX + user.getId();
        redisTemplate.opsForSet().add(userTokensKey, tokenId);
        redisTemplate.expire(userTokensKey, jwtServiceImpl.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

        setTokenCookie(response, AccessToken, accessToken, jwtServiceImpl.getAccessTokenExpiration());
        setTokenCookie(response, RefreshToken, refreshToken, jwtServiceImpl.getRefreshTokenExpiration());

        return ActionMessageResponse.builder()
                .message("Đăng nhập thành công")
                .build();
    }

    @Override
    public ActionMessageResponse googleLogin(GoogleLoginRequest googleLoginRequest, HttpServletResponse response) {
        GoogleUserInfo googleUserInfo = googleIdTokenVerifierService.verify(googleLoginRequest.getIdToken());
        Optional<User> existingUser = userRepository.findByEmail(googleUserInfo.email());

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (isLockedOrDeleted(user)) {
                throw new BusinessException(LOGIN_FAILED);
            }
            if (!GOOGLE_PROVIDER.equalsIgnoreCase(user.getProvider())) {
                throw new BusinessException(EMAIL_EXISTED);
            }
            if (user.getProviderId() == null || !user.getProviderId().equals(googleUserInfo.providerId())) {
                throw new BusinessException(LOGIN_FAILED);
            }
        } else {
            user = registerGoogleCustomer(googleUserInfo);
        }

        issueAuthTokens(user, response);
        return ActionMessageResponse.builder()
                .message("ÄÄƒng nháº­p thÃ nh cÃ´ng")
                .build();
    }

    @Override
    public void googleAuthorize(HttpServletResponse response) {
        String state = UUID.randomUUID().toString();
        String stateKey = GOOGLE_STATE_PREFIX + state;

        try {
            redisTemplate.opsForValue().set(stateKey, state, googleStateTtlMinutes, TimeUnit.MINUTES);
            String authorizationUrl = googleOAuthService.buildAuthorizationUrl(state);
            log.info("Google OAuth authorize prepared: stateKey={} stateLength={} ttlMinutes={} redirectUrl={}",
                    stateKey, safeLength(state), googleStateTtlMinutes, authorizationUrl);
            redirect(response, authorizationUrl);
        } catch (Exception ex) {
            log.error("Failed to start Google OAuth flow: stateKey={} stateLength={}", stateKey, safeLength(state), ex);
            redisTemplate.delete(stateKey);
            redirect(response, buildGoogleFailureRedirectUrl(GOOGLE_ERROR_REASON_LOGIN_FAILED));
        }
    }

    @Override
    public void googleCallback(String code, String state, String error, HttpServletResponse response) {
        log.info(
                "Google OAuth callback received: codePresent={} codeLength={} statePresent={} stateLength={} error={}",
                hasText(code), safeLength(code), hasText(state), safeLength(state), error);
        String stateKey = validateGoogleState(state);
        if (stateKey == null) {
            log.warn("Google OAuth callback state invalid: statePresent={} stateLength={}",
                    hasText(state), safeLength(state));
            redirect(response, buildGoogleFailureRedirectUrl(GOOGLE_ERROR_REASON_STATE_INVALID));
            return;
        }

        try {
            log.info("Google OAuth callback state validated: stateKey={}", stateKey);
            redisTemplate.delete(stateKey);
            log.info("Google OAuth callback state deleted: stateKey={}", stateKey);

            if (error != null && !error.isBlank()) {
                log.warn("Google OAuth callback denied by provider: error={}", error);
                redirect(response, buildGoogleFailureRedirectUrl(GOOGLE_ERROR_REASON_OAUTH_DENIED));
                return;
            }

            if (code == null || code.isBlank()) {
                log.warn("Google OAuth callback missing authorization code");
                redirect(response, buildGoogleFailureRedirectUrl(GOOGLE_ERROR_REASON_CODE_EXCHANGE_FAILED));
                return;
            }

            GoogleUserInfo googleUserInfo = googleOAuthService.exchangeCode(code);
            User user = resolveGoogleUser(googleUserInfo);
            issueAuthTokens(user, response);
            log.info("Google OAuth callback success: userId={} role={} provider={} providerId={}",
                    user.getId(), user.getRole(), user.getProvider(), maskProviderId(user.getProviderId()));
            redirect(response, buildGoogleSuccessRedirectUrl());
        } catch (GoogleOAuthFlowException ex) {
            markRollbackOnlyIfPossible();
            log.warn("Google OAuth callback failed in exchange/verify phase: reason={} codeLength={} stateLength={}",
                    ex.getReason(), safeLength(code), safeLength(state), ex);
            redirect(response, buildGoogleFailureRedirectUrl(ex.getReason()));
        } catch (BusinessException ex) {
            markRollbackOnlyIfPossible();
            log.warn("Google OAuth callback business failure: errorCode={} mappedReason={}",
                    ex.getErrorCode(), mapGoogleLoginFailureReason(ex), ex);
            redirect(response, buildGoogleFailureRedirectUrl(mapGoogleLoginFailureReason(ex)));
        } catch (Exception ex) {
            markRollbackOnlyIfPossible();
            log.error("Google OAuth callback failed unexpectedly: codeLength={} stateLength={} error={}",
                    safeLength(code), safeLength(state), error, ex);
            redirect(response, buildGoogleFailureRedirectUrl(GOOGLE_ERROR_REASON_LOGIN_FAILED));
        }
    }

    private User resolveGoogleUser(GoogleUserInfo googleUserInfo) {
        log.info("Resolving Google user: email={} providerId={}",
                maskEmail(googleUserInfo.email()), maskProviderId(googleUserInfo.providerId()));
        Optional<User> existingUser = userRepository.findByEmail(googleUserInfo.email());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("Found existing user for Google login: userId={} provider={} status={} providerId={}",
                    user.getId(), user.getProvider(), user.getStatus(), maskProviderId(user.getProviderId()));
            if (isLockedOrDeleted(user)) {
                throw new BusinessException(LOGIN_FAILED);
            }
            if (!GOOGLE_PROVIDER.equalsIgnoreCase(user.getProvider())) {
                throw new BusinessException(EMAIL_EXISTED);
            }
            if (user.getProviderId() == null || !user.getProviderId().equals(googleUserInfo.providerId())) {
                throw new BusinessException(LOGIN_FAILED);
            }
            return user;
        }

        return registerGoogleCustomer(googleUserInfo);
    }

    private String validateGoogleState(String state) {
        if (state == null || state.isBlank()) {
            log.warn("Google OAuth state missing/blank");
            return null;
        }

        String stateKey = GOOGLE_STATE_PREFIX + state;
        Object storedState = redisTemplate.opsForValue().get(stateKey);
        if (storedState == null || !state.equals(storedState.toString())) {
            log.warn("Google OAuth state mismatch: stateKey={} storedStatePresent={} incomingStateLength={}",
                    stateKey, storedState != null, safeLength(state));
            return null;
        }

        return stateKey;
    }

    private User registerGoogleCustomer(GoogleUserInfo googleUserInfo) {
        log.info("Registering new Google customer: email={} providerId={}",
                maskEmail(googleUserInfo.email()), maskProviderId(googleUserInfo.providerId()));
        User newUser = User.builder()
                .email(googleUserInfo.email())
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .provider(GOOGLE_PROVIDER)
                .providerId(googleUserInfo.providerId())
                .role(UserEnum.UserRole.CUSTOMER)
                .status(UserEnum.UserStatus.ACTIVE)
                .build();
        User savedUser = userRepository.save(newUser);
        log.info("Google user persisted in identity-service: userId={} email={}",
                savedUser.getId(), maskEmail(savedUser.getEmail()));

        try {
            RegisterCustomerRequest userServiceRequest = RegisterCustomerRequest.builder()
                    .id(savedUser.getId())
                    .name(resolveGoogleDisplayName(googleUserInfo))
                    .email(savedUser.getEmail())
                    .dob(DEFAULT_GOOGLE_DOB)
                    .gender(DEFAULT_GOOGLE_GENDER)
                    .phone(DEFAULT_GOOGLE_PHONE)
                    .role(savedUser.getRole())
                    .build();

            userGrpcClient.createCustomerProfile(userServiceRequest);
            log.info("Google user profile created in user-service: userId={} email={}",
                    savedUser.getId(), maskEmail(savedUser.getEmail()));
        } catch (BusinessException e) {
            log.error("Failed to create Google user profile in user-service: userId={} email={} errorCode={}",
                    savedUser.getId(), maskEmail(savedUser.getEmail()), e.getErrorCode(), e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Google user profile in user-service: userId={} email={}",
                    savedUser.getId(), maskEmail(savedUser.getEmail()), e);
            throw new BusinessException(ErrorCode.NOT_CREATED);
        }

        return savedUser;
    }

    private String resolveGoogleDisplayName(GoogleUserInfo googleUserInfo) {
        String googleName = googleUserInfo.name();
        if (googleName != null && !googleName.isBlank()) {
            return googleName;
        }

        String email = googleUserInfo.email();
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private void issueAuthTokens(User user, HttpServletResponse response) {
        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtServiceImpl.generateAccessToken(user, tokenId);
        String refreshToken = jwtServiceImpl.generateRefreshToken(user, tokenId);

        String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
        String refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenId;

        redisTemplate.opsForValue().set(
                accessTokenKey,
                user.getId().toString(),
                jwtServiceImpl.getAccessTokenExpiration() - TOKEN_EXPIRY_BUFFER,
                TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(
                refreshTokenKey,
                user.getId().toString(),
                jwtServiceImpl.getRefreshTokenExpiration(),
                TimeUnit.MILLISECONDS);

        String userTokensKey = USER_TOKENS_PREFIX + user.getId();
        redisTemplate.opsForSet().add(userTokensKey, tokenId);
        redisTemplate.expire(userTokensKey, jwtServiceImpl.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

        setTokenCookie(response, AccessToken, accessToken, jwtServiceImpl.getAccessTokenExpiration());
        setTokenCookie(response, RefreshToken, refreshToken, jwtServiceImpl.getRefreshTokenExpiration());
    }

    private void redirect(HttpServletResponse response, String url) {
        try {
            response.sendRedirect(url);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to redirect response", ex);
        }
    }

    private String buildGoogleSuccessRedirectUrl() {
        return UriComponentsBuilder.fromUriString(googleSuccessRedirectUrl)
                .queryParam(GOOGLE_OAUTH_PARAM, GOOGLE_OAUTH_PARAM_VALUE)
                .queryParam(GOOGLE_STATUS_PARAM, GOOGLE_STATUS_SUCCESS)
                .build()
                .encode()
                .toUriString();
    }

    private String buildGoogleFailureRedirectUrl(String reason) {
        return UriComponentsBuilder.fromUriString(googleFailureRedirectUrl)
                .queryParam(GOOGLE_OAUTH_PARAM, GOOGLE_OAUTH_PARAM_VALUE)
                .queryParam(GOOGLE_STATUS_PARAM, GOOGLE_STATUS_ERROR)
                .queryParam("code", reason)
                .build()
                .encode()
                .toUriString();
    }

    private String mapGoogleLoginFailureReason(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode == EMAIL_EXISTED) {
            return GOOGLE_ERROR_REASON_EMAIL_CONFLICT;
        }
        if (errorCode == NOT_CREATED) {
            return GOOGLE_ERROR_REASON_PROFILE_CREATION_FAILED;
        }
        return GOOGLE_ERROR_REASON_LOGIN_FAILED;
    }

    private void markRollbackOnlyIfPossible() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // No active transaction in unit tests or non-transactional callers.
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "<empty>";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String maskProviderId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return "<empty>";
        }
        if (providerId.length() <= 8) {
            return "***";
        }
        return providerId.substring(0, 4) + "..." + providerId.substring(providerId.length() - 3);
    }

    // Revoke all active tokens for current user session scope.
    @Override
    public ActionMessageResponse logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = extractAccessTokenFromRequest(request);
        try {
            if (accessToken != null && !accessToken.isBlank()) {
                UUID userId = jwtServiceImpl.extractUserId(accessToken);
                String userTokensKey = USER_TOKENS_PREFIX + userId;
                Set<Object> tokenIds = redisTemplate.opsForSet().members(userTokensKey);
                if (tokenIds != null && !tokenIds.isEmpty()) {
                    List<String> keysToDelete = new ArrayList<>();
                    for (Object tokenId : tokenIds) {
                        String tokenIdStr = (String) tokenId;
                        String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenIdStr;
                        String refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenIdStr;
                        keysToDelete.add(accessTokenKey);
                        keysToDelete.add(refreshTokenKey);
                    }
                    redisTemplate.delete(keysToDelete);
                }
                redisTemplate.delete(userTokensKey);
            }
        } catch (Exception e) {
            log.error("Exception : {}", e.getMessage());
        } finally {
            clearAuthCookies(response);
        }
        return ActionMessageResponse.builder()
                .message("Đăng xuất thành công")
                .build();
    }

    // Rotate refresh token and issue a new access token while preserving refresh
    // TTL.
    @Override
    public ActionMessageResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String cookieRefreshToken = extractCookieValue(request, RefreshToken);

        // Neu refresh_token khong co trong cookie
        if (cookieRefreshToken == null) {
            clearAuthCookies(response);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }

        try {
            String tokenId = jwtServiceImpl.extractTokenId(cookieRefreshToken);
            String tokenType = jwtServiceImpl.extractTokenType(cookieRefreshToken);
            UUID userId = jwtServiceImpl.extractUserId(cookieRefreshToken);
            String refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenId;
            String userIdStr = (String) redisTemplate.opsForValue().get(refreshTokenKey);
            long expirationRefreshToken = jwtServiceImpl.extractExpirationMillis(cookieRefreshToken);
            long ttlMillis = expirationRefreshToken - System.currentTimeMillis();

            if (!"refresh".equals(tokenType)) {
                throw new BusinessException(REFRESH_TOKEN_MISSING);
            }

            // Neu refresh token het han
            if (userIdStr == null) {
                throw new BusinessException(REFRESH_TOKEN_MISSING);
            }

            UUID redisUserId = UUID.fromString(userIdStr);

            if (!redisUserId.equals(userId)) {
                log.warn("Refresh token revoked or reused, tokenId={}", tokenId);
                throw new BusinessException(REFRESH_TOKEN_MISSING);
            }

            String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
            Boolean hasAccessToken = redisTemplate.hasKey(accessTokenKey);
            String userTokensKey = USER_TOKENS_PREFIX + userId;

            redisTemplate.delete(refreshTokenKey);
            //Nếu vẫn còn AccessToken trên redis
            if (Boolean.TRUE.equals(hasAccessToken)) {
                redisTemplate.delete(accessTokenKey);
                redisTemplate.opsForSet().remove(userTokensKey, tokenId);
                throw new BusinessException(REFRESH_TOKEN_MISSING);
            }

            redisTemplate.opsForSet().remove(userTokensKey, tokenId);

            // Tao token id moi
            tokenId = UUID.randomUUID().toString();
            accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
            refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenId;

            // Tao rotate token va luu vao redis
            redisTemplate.opsForValue().set(accessTokenKey, userId.toString(), jwtServiceImpl.getAccessTokenExpiration(),
                    TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(refreshTokenKey, userId.toString(), ttlMillis, TimeUnit.MILLISECONDS);
            redisTemplate.opsForSet().add(userTokensKey, tokenId);
            redisTemplate.expire(userTokensKey, jwtServiceImpl.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

            User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(REFRESH_TOKEN_MISSING));
            String accessToken = jwtServiceImpl.generateAccessToken(user, tokenId);
            String refreshToken = jwtServiceImpl.generateRefreshTokenWithExp(user, tokenId, ttlMillis);

            setTokenCookie(response, AccessToken, accessToken, jwtServiceImpl.getAccessTokenExpiration());
            setTokenCookie(response, RefreshToken, refreshToken, ttlMillis);
            return ActionMessageResponse.builder()
                    .message("Làm mới phiên đăng nhập thành công")
                    .build();
        } catch (BusinessException e) {
            clearAuthCookies(response);
            throw e;
        } catch (Exception e) {
            // Neu nguoi dung sua gia tri refresh_token tren cookie
            log.warn("Refresh token error", e);
            clearAuthCookies(response);
            throw new BusinessException(REFRESH_TOKEN_MISSING);
        }
    }

    private void setTokenCookie(HttpServletResponse response, String cookieName, String value, long ttlMillis) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(authCookieSecure)
                .path("/")
                .sameSite(authCookieSameSite)
                .maxAge(Duration.ofMillis(Math.max(ttlMillis, 0)))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearTokenCookie(HttpServletResponse response, String cookieName) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(authCookieSecure)
                .path("/")
                .sameSite(authCookieSameSite)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        clearTokenCookie(response, AccessToken);
        clearTokenCookie(response, RefreshToken);
    }

    private String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String extractAccessTokenFromRequest(HttpServletRequest request) {
        String tokenFromCookie = extractCookieValue(request, AccessToken);
        if (tokenFromCookie != null && !tokenFromCookie.isBlank()) {
            return tokenFromCookie;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private OtpData getOtpData(String otpKey) {
        Object rawValue = redisTemplate.opsForValue().get(otpKey);
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof OtpData otpData) {
            return otpData;
        }

        if (rawValue instanceof Map<?, ?> map) {
            OtpData otpData = mapToOtpData(map);
            if (otpData == null) {
                log.error("Invalid OTP payload in Redis key={} type={}", otpKey, rawValue.getClass().getName());
                throw new BusinessException(OTP_INVALID);
            }

            Long ttlMillis = redisTemplate.getExpire(otpKey, TimeUnit.MILLISECONDS);
            if (ttlMillis != null && ttlMillis > 0) {
                redisTemplate.opsForValue().set(otpKey, otpData, ttlMillis, TimeUnit.MILLISECONDS);
            } else {
                redisTemplate.opsForValue().set(otpKey, otpData);
            }
            log.warn("Converted legacy OTP payload key={} from type={} to OtpData",
                    otpKey, rawValue.getClass().getSimpleName());
            return otpData;
        }

        log.error("Unsupported OTP payload type in Redis key={} type={}", otpKey, rawValue.getClass().getName());
        throw new BusinessException(OTP_INVALID);
    }

    private OtpData mapToOtpData(Map<?, ?> source) {
        try {
            OtpData.OtpPurpose purpose = parseOtpPurpose(source.get("purpose"));
            RegisterCustomerRequest registerCustomerRequest = toRegisterCustomerRequest(source.get("registerCustomerRequest"));
            ForgotPasswordRequest forgotPasswordRequest = toForgotPasswordRequest(source.get("forgotPasswordRequest"));

            return OtpData.builder()
                    .verifyToken(asString(source.get("verifyToken")))
                    .subject(asString(source.get("subject")))
                    .otpHash(asString(source.get("otpHash")))
                    .purpose(purpose)
                    .expiredAt(parseLocalDateTime(source.get("expiredAt")))
                    .lastSentAt(parseLocalDateTime(source.get("lastSentAt")))
                    .registerCustomerRequest(registerCustomerRequest)
                    .forgotPasswordRequest(forgotPasswordRequest)
                    .sendCount(asInt(source.get("sendCount"), 1))
                    .verifyAttempts(asInt(source.get("verifyAttempts"), 0))
                    .build();
        } catch (Exception ex) {
            log.error("Failed to convert legacy OTP payload from Redis", ex);
            return null;
        }
    }

    private RegisterCustomerRequest toRegisterCustomerRequest(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof RegisterCustomerRequest request) {
            return request;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }

        RegisterCustomerRequest.RegisterCustomerRequestBuilder builder = RegisterCustomerRequest.builder();
        builder.name(asString(map.get("name")));
        builder.email(asString(map.get("email")));
        builder.password(asString(map.get("password")));
        builder.phone(asString(map.get("phone")));
        builder.dob(parseLocalDate(map.get("dob")));
        builder.gender(parseGender(map.get("gender")));
        builder.role(parseUserRole(map.get("role")));
        builder.id(parseUuid(map.get("id")));
        return builder.build();
    }

    private ForgotPasswordRequest toForgotPasswordRequest(Object value) {
        // Current flows rely on subject for forgot-password OTP, so this field is optional.
        return null;
    }

    private boolean isLockedOrDeleted(User user) {
        return user.getStatus() == UserEnum.UserStatus.LOCKED || Boolean.TRUE.equals(user.getIsDeleted());
    }

    private OtpData.OtpPurpose parseOtpPurpose(Object value) {
        String raw = asString(value);
        return raw == null ? null : OtpData.OtpPurpose.valueOf(raw);
    }

    private UserEnum.Gender parseGender(Object value) {
        String raw = asString(value);
        return raw == null ? null : UserEnum.Gender.valueOf(raw);
    }

    private UserEnum.UserRole parseUserRole(Object value) {
        String raw = asString(value);
        return raw == null ? null : UserEnum.UserRole.valueOf(raw);
    }

    private UUID parseUuid(Object value) {
        String raw = asString(value);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof List<?> list && list.size() >= 3) {
            return LocalDate.of(asInt(list.get(0), 1970), asInt(list.get(1), 1), asInt(list.get(2), 1));
        }
        return LocalDate.parse(value.toString());
    }

    private LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof List<?> list && list.size() >= 6) {
            int nano = list.size() >= 7 ? asInt(list.get(6), 0) : 0;
            return LocalDateTime.of(
                    asInt(list.get(0), 1970),
                    asInt(list.get(1), 1),
                    asInt(list.get(2), 1),
                    asInt(list.get(3), 0),
                    asInt(list.get(4), 0),
                    asInt(list.get(5), 0),
                    nano
            );
        }
        return LocalDateTime.parse(value.toString());
    }
}
