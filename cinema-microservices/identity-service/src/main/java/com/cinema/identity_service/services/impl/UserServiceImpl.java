package com.cinema.identity_service.services.impl;

import com.cinema.Enum.UserEnum;
import com.cinema.dto.request.SendEmailRequest;
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
import com.cinema.identity_service.dto.response.ChangePasswordResponse;
import com.cinema.identity_service.dto.response.ForgotPasswordResponse;
import com.cinema.identity_service.dto.response.LoginResponse;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.dto.response.VerifyResponse;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
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

    static String VerifyToken = "verifyToken";
    static String RefreshToken = "refreshToken";

    static String OTP_PREFIX = "otp:";
    static String OTP_SUBJECT_PREFIX = "otp:subject:";
    static String ACCESS_TOKEN_PREFIX = "token:access:";
    static String REFRESH_TOKEN_PREFIX = "token:refresh:";
    static String USER_TOKENS_PREFIX = "user_tokens:";

    static Pattern STRONG_PASSWORD_PATTERN = Pattern
            .compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,32}$");
    static int MAX_VERIFY_ATTEMPTS = 3;
    static int MAX_SEND_COUNT = 4;

    @Override
    public RegisterCustomerResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest,
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
        return RegisterCustomerResponse.builder()
                .message("Mã OTP đã được gửi đến email của bạn")
                .build();
    }

    @Override
    public RegisterCustomerResponse createManager(RegisterManagerRequest registerManagerRequest,
            HttpServletRequest request) {
        // Kiểm tra email đã tồn tại
        if (userRepository.existsByEmail(registerManagerRequest.getEmail())) {
            throw new BusinessException(EMAIL_EXISTED);
        }

        // Tạo manager request với password mã hóa
        RegisterManagerRequest managerRequest = RegisterManagerRequest.builder()
                .name(registerManagerRequest.getName())
                .email(registerManagerRequest.getEmail())
                .password(passwordEncoder.encode(registerManagerRequest.getPassword()))
                .dob(registerManagerRequest.getDob())
                .phone(registerManagerRequest.getPhone())
                .gender(registerManagerRequest.getGender())
                .build();

        // Lưu user vào identity-service database
        User newManager = userMapper.toUser(managerRequest);
        User savedManager = userRepository.save(newManager);

        try {
            // Gọi user-service để tạo profile manager
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

        } catch (Exception e) {
            log.error("Failed to create manager in user-service: email={}", managerRequest.getEmail(), e);
            throw new BusinessException(ErrorCode.NOT_CREATED);
        }
        // Gửi email chào mừng cho manager
        internalEmailDispatchService.sendAsync(new SendEmailRequest(
                registerManagerRequest.getEmail(),
                "Chào mừng bạn trở thành Manager",
                "Tài khoản Manager đã được tạo thành công.",
                "Chúc mừng bạn đã trở thành Manager tại CinemaStar!"));
        log.info("Welcome email dispatch queued for manager: email={}", registerManagerRequest.getEmail());
        return RegisterCustomerResponse.builder()
                .message("Tạo manager thành công")
                .build();
    }

    @Override
    public RegisterCustomerResponse createStaff(RegisterStaffRequest registerStaffRequest,
            HttpServletRequest request) {
        String role = request.getHeader(HeaderNames.X_USER_ROLE);
        if (!(HeaderNames.ROLE_ADMIN.equals(role) || HeaderNames.ROLE_MANAGER.equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // Kiểm tra email đã tồn tại
        if (userRepository.existsByEmail(registerStaffRequest.getEmail())) {
            throw new BusinessException(EMAIL_EXISTED);
        }

        // Tạo staff request với password mã hóa
        RegisterStaffRequest staffRequest = RegisterStaffRequest.builder()
                .name(registerStaffRequest.getName())
                .email(registerStaffRequest.getEmail())
                .password(passwordEncoder.encode(registerStaffRequest.getPassword()))
                .dob(registerStaffRequest.getDob())
                .phone(registerStaffRequest.getPhone())
                .gender(registerStaffRequest.getGender())
                .build();

        // Lưu user vào identity-service database
        User newStaff = userMapper.toUser(staffRequest);
        User savedStaff = userRepository.save(newStaff);

        try {
            // Gọi user-service để tạo profile staff
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

        return RegisterCustomerResponse.builder()
                .message("Tạo staff thành công")
                .build();
    }

    @Override
    public void resendOTP(HttpServletRequest request) {
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
        OtpData otpData = (OtpData) redisTemplate.opsForValue().get(otpKey);
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
    }

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest forgotPasswordRequest,
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

        return ForgotPasswordResponse.builder()
                .message("Mã OTP đã được gửi đến email của bạn")
                .build();
    }

    @Override
    public ChangePasswordResponse changePassword(ChangePasswordRequest changePasswordRequest,
            HttpServletRequest request) {

        // Kiểm tra mật khẩu cũ và mật khẩu mới có giống nhau hay không
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

        // Kiểm tra mật khẩu cũ có đúng hay không
        if (!passwordEncoder.matches(changePasswordRequest.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }

        // Update password (validation đã được kiểm tra ở ChangePasswordRequest)
        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", userId);
        return ChangePasswordResponse.builder()
                .message("Đổi mật khẩu thành công")
                .build();
    }

    @Override
    public VerifyResponse verifyOTP(VerifyRequest verifyRequest, HttpServletRequest request) {
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
        OtpData otpData = (OtpData) redisTemplate.opsForValue().get(otpKey);
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
                System.out.println("Role of request: " + registerCustomerRequest.getRole());

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

        return VerifyResponse.builder()
                .message("Xác thực OTP thành công")
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest, HttpServletResponse response) {
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

        redisTemplate.opsForValue().set(accessTokenKey, user.getId(), jwtServiceImpl.getAccessTokenExpiration(),
                TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(refreshTokenKey, user.getId(), jwtServiceImpl.getRefreshTokenExpiration(),
                TimeUnit.MILLISECONDS);

        String userTokensKey = USER_TOKENS_PREFIX + user.getId();
        redisTemplate.opsForSet().add(userTokensKey, tokenId);
        redisTemplate.expire(userTokensKey, jwtServiceImpl.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

        Cookie refreshTokenCookie = new Cookie(RefreshToken, refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(false);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge((int) (jwtServiceImpl.getRefreshTokenExpiration() / 1000));
        response.addCookie(refreshTokenCookie);

        return LoginResponse.builder().accessToken(accessToken).build();
    }

    @Override
    public void logout(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String accessToken = authHeader.substring(7);
        try {
            UUID userId = jwtServiceImpl.extractUserId(accessToken);
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            Set<Object> tokenIds = redisTemplate.opsForSet().members(userTokensKey);
            if (tokenIds != null && !tokenIds.isEmpty()) {
                for (Object tokenId : tokenIds) {
                    String tokenIdStr = (String) tokenId;
                    String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenIdStr;
                    String refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenIdStr;
                    redisTemplate.delete(accessTokenKey);
                    redisTemplate.delete(refreshTokenKey);

                }
            }
            redisTemplate.delete(userTokensKey);
        } catch (Exception e) {
            log.error("Exception : {}", e.getMessage());
        }
    }

    @Override
    public LoginResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String cookieRefreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (RefreshToken.equals(cookie.getName())) {
                    cookieRefreshToken = cookie.getValue();
                    break;
                }
            }
        }

        // Nếu refresh_token không có trong cookie
        if (cookieRefreshToken == null) {
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

            // Nếu refreshtoken hết hạn
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
            // Nếu còn token trên redis
            if (Boolean.TRUE.equals(hasAccessToken)) {
                redisTemplate.delete(accessTokenKey);
                redisTemplate.opsForSet().remove(userTokensKey, tokenId);
                throw new BusinessException(REFRESH_TOKEN_MISSING);
            }

            redisTemplate.opsForSet().remove(userTokensKey, tokenId);

            // tạo id mới
            tokenId = UUID.randomUUID().toString();
            accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
            refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenId;

            // Tạo rotate token và thêm vào redis
            redisTemplate.opsForValue().set(accessTokenKey, userId, jwtServiceImpl.getAccessTokenExpiration(),
                    TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(refreshTokenKey, userId, ttlMillis, TimeUnit.MILLISECONDS);
            redisTemplate.opsForSet().add(userTokensKey, tokenId);
            redisTemplate.expire(userTokensKey, jwtServiceImpl.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

            User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(REFRESH_TOKEN_MISSING));
            String accessToken = jwtServiceImpl.generateAccessToken(user, tokenId);
            String refreshToken = jwtServiceImpl.generateRefreshTokenWithExp(user, tokenId, ttlMillis);

            Cookie refreshTokenCookie = new Cookie(RefreshToken, refreshToken);
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(false);
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge((int) (ttlMillis / 1000));
            response.addCookie(refreshTokenCookie);
            return LoginResponse.builder().accessToken(accessToken).build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Nếu người dùng sửa giá trị refresh_token trên cookie
            log.warn("Refresh token error", e);
            throw new BusinessException(REFRESH_TOKEN_MISSING);
        }
    }
}
