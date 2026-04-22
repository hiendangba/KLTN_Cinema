package com.cinema.identity_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.dto.request.SendEmailRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.identity_service.dto.request.ChangePasswordRequest;
import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
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

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
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
    final InternalEmailDispatchService internalEmailDispatchService;
    @Value("${app.auth.cookie.secure:true}")
    boolean authCookieSecure;
    @Value("${app.auth.cookie.same-site:None}")
    String authCookieSameSite;

    static String VerifyToken = "verifyToken";
    static String AccessToken = "accessToken";
    static String RefreshToken = "refreshToken";

    static String OTP_PREFIX = "identity:otp:";
    static String OTP_SUBJECT_PREFIX = "identity:otp:subject:";
    static String ACCESS_TOKEN_PREFIX = "identity:token:access:";
    static String REFRESH_TOKEN_PREFIX = "identity:token:refresh:";
    static String USER_TOKENS_PREFIX = "identity:user_tokens:";

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
        Cookie refreshTokenCookie = new Cookie(VerifyToken, verifyToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(300);
        response.addCookie(refreshTokenCookie);

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
        String role = request.getHeader(HeaderNames.X_USER_ROLE);
        if (!(HeaderNames.ROLE_ADMIN.equals(role) || HeaderNames.ROLE_MANAGER.equals(role))) {
            log.warn("Forbidden createStaff request: requiredRoles=[{},{}] actualRole={} userId={} method={} path={}",
                    HeaderNames.ROLE_ADMIN,
                    HeaderNames.ROLE_MANAGER,
                    role,
                    request.getHeader(HeaderNames.X_USER_ID),
                    request.getMethod(),
                    request.getRequestURI());
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

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
        Cookie refreshTokenCookie = new Cookie(VerifyToken, verifyToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(300);
        response.addCookie(refreshTokenCookie);
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

        // Kiem tra mat khau cu va mat khau moi co giong nhau hay khong
        if (changePasswordRequest.getOldPassword().equals(changePasswordRequest.getNewPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_DUPLICATED);
        }

        String userId = request.getHeader(HeaderNames.X_USER_ID);

        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UUID userUUID;

        try {
            userUUID = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        User user = userRepository.findById(userUUID)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));

        // Kiem tra mat khau cu co dung hay khong
        if (!passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }

        // Update password (validation da duoc kiem tra o ChangePasswordRequest)
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", userId);
        return ActionMessageResponse.builder()
                .message("Đổi mật khẩu thành công")
                .build();
    }

    // Verify OTP and execute follow-up action (register account or reset password).
    @Override
    public ActionMessageResponse verifyOTP(VerifyRequest verifyRequest, HttpServletRequest request) {
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
        if (user.getStatus().equals(UserEnum.UserStatus.LOCKED)) {
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

        redisTemplate.opsForValue().set(accessTokenKey, user.getId().toString(), jwtServiceImpl.getAccessTokenExpiration(),
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
            // Neu van con access token tren redis
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
