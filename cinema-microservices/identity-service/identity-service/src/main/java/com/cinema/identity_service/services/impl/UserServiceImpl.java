package com.cinema.identity_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.identity_service.dto.request.ForgotPasswordRequest;
import com.cinema.identity_service.dto.request.LoginRequest;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import com.cinema.identity_service.dto.response.ForgotPasswordResponse;
import com.cinema.identity_service.dto.response.LoginResponse;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.dto.response.VerifyResponse;
import com.cinema.identity_service.entity.OtpData;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.mapper.UserMapper;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.cinema.exception.ErrorCode.*;
@FieldDefaults(level = AccessLevel.PRIVATE,makeFinal = true)
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    JwtServiceImpl jwtServiceImpl;
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    @Qualifier("otpEncoder")
    PasswordEncoder otpEncoder;
    UserMapper userMapper;
    RedisTemplate<String, Object> redisTemplate;

    static String VerifyToken = "verifyToken";
    static String RefreshToken = "refreshToken";

    static String OTP_PREFIX = "otp:";
    static String OTP_SUBJECT_PREFIX = "otp:subject:";
    static String ACCESS_TOKEN_PREFIX = "token:access:";
    static String REFRESH_TOKEN_PREFIX = "token:refresh:";
    static String USER_TOKENS_PREFIX = "user_tokens:";

    static Pattern STRONG_PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,32}$");
    static int MAX_VERIFY_ATTEMPTS = 3;
    static int MAX_SEND_COUNT = 4;

    @Override
    public RegisterCustomerResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest, HttpServletResponse response) {
        if(userRepository.existsByEmail(registerCustomerRequest.getEmail())){
            throw new BusinessException(EMAIL_EXISTED);
        }


        RegisterCustomerRequest customerRequest  = RegisterCustomerRequest.builder()
                                                    .name(registerCustomerRequest.getName())
                                                    .email(registerCustomerRequest.getEmail())
                                                    .password(passwordEncoder.encode(registerCustomerRequest.getPassword()))
                                                    .dob(registerCustomerRequest.getDob())
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
        //Gửi gmail

        return RegisterCustomerResponse.builder()
                .message("Mã OTP đã được gửi đến email của bạn")
                .build();
    }

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest forgotPasswordRequest, HttpServletResponse response) {
        if(!userRepository.existsByEmail(forgotPasswordRequest.getEmail())){
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
        //Gửi gmail
        return ForgotPasswordResponse.builder()
                .message("Mã OTP đã được gửi đến email của bạn")
                .build();


    }

    @Override
    public void resendOTP(HttpServletRequest request) {
        String cookieVerifyToken  = null;
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
        String redisVerifyToken  = (String) redisTemplate.opsForValue().get(subjectKey);

        if(redisVerifyToken == null){
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
        redisTemplate.opsForValue().set(OTP_SUBJECT_PREFIX + otpData.getSubject(), cookieVerifyToken, 5, TimeUnit.MINUTES);

        //Gửi gmail
    }

    @Override
    public VerifyResponse verifyOTP(VerifyRequest verifyRequest, HttpServletRequest request) {
        String cookieVerifyToken  = null;
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
        String redisVerifyToken  = (String) redisTemplate.opsForValue().get(subjectKey);

        if(redisVerifyToken == null){
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
            redisTemplate.opsForValue().set(otpKey, otpData, Duration.between(LocalDateTime.now(), otpData.getExpiredAt()));
            throw new BusinessException(OTP_INVALID);
        }
        log.info("OTP purpose = {}", otpData.getPurpose());
        switch (otpData.getPurpose()) {
            case REGISTER:
                RegisterCustomerRequest registerCustomerRequest = otpData.getRegisterCustomerRequest();
                User user_register = userMapper.toUser(registerCustomerRequest);
                userRepository.save(user_register);
                break;
            case FORGOT_PASSWORD:
                if (verifyRequest.getPassword() == null) {
                    otpData.setVerifyAttempts(otpData.getVerifyAttempts() + 1);
                    redisTemplate.opsForValue().set(otpKey, otpData, Duration.between(LocalDateTime.now(), otpData.getExpiredAt()));
                    throw new BusinessException(PASSWORD_REQUIRED);
                }

                if (!STRONG_PASSWORD_PATTERN.matcher(verifyRequest.getPassword()).matches()) {
                    otpData.setVerifyAttempts(otpData.getVerifyAttempts() + 1);
                    redisTemplate.opsForValue().set(otpKey, otpData, Duration.between(LocalDateTime.now(), otpData.getExpiredAt()));
                    throw new BusinessException(PASSWORD_INVALID);
                }
                User user_forgotPassword = userRepository.findByEmail(otpData.getSubject())
                        .orElseThrow(() -> {
                            otpData.setVerifyAttempts(otpData.getVerifyAttempts() + 1);
                            redisTemplate.opsForValue().set(
                                    otpKey,
                                    otpData,
                                    Duration.between(LocalDateTime.now(), otpData.getExpiredAt())
                            );
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
        if (user.getStatus().equals(User.UserStatus.LOCKED)) {
            throw new BusinessException(LOGIN_FAILED);
        }
        if(!passwordEncoder.matches(loginRequest.getPassword(),user.getPassword())){
            throw new BusinessException(LOGIN_FAILED);
        }

        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtServiceImpl.generateAccessToken(user, tokenId);
        String refreshToken = jwtServiceImpl.generateRefreshToken(user, tokenId);

        String accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
        String refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenId;

        redisTemplate.opsForValue().set(accessTokenKey, user.getId(), jwtServiceImpl.getAccessTokenExpiration(), TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(refreshTokenKey, user.getId(), jwtServiceImpl.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);

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
        try{
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
        }
        catch (Exception e){
            log.error("Exception : {}", e.getMessage());
        }
    }

    @Override
    public LoginResponse refreshToken(HttpServletRequest request, HttpServletResponse response){
        String cookieRefreshToken  = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (RefreshToken.equals(cookie.getName())) {
                    cookieRefreshToken = cookie.getValue();
                    break;
                }
            }
        }

        //Nếu refresh_token không có trong cookie
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

            //Nếu refreshtoken hết hạn
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
            //Nếu còn token trên redis
            if (Boolean.TRUE.equals(hasAccessToken)) {
                redisTemplate.delete(accessTokenKey);
                redisTemplate.opsForSet().remove(userTokensKey, tokenId);
                throw new BusinessException(REFRESH_TOKEN_MISSING);
            }

            redisTemplate.opsForSet().remove(userTokensKey, tokenId);

            //tạo id mới
            tokenId = UUID.randomUUID().toString();
            accessTokenKey = ACCESS_TOKEN_PREFIX + tokenId;
            refreshTokenKey = REFRESH_TOKEN_PREFIX + tokenId;

            //Tạo rotate token và thêm vào redis
            redisTemplate.opsForValue().set(accessTokenKey, userId, jwtServiceImpl.getAccessTokenExpiration(), TimeUnit.MILLISECONDS);
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
            //Nếu người dùng sửa giá trị refresh_token trên cookie
            log.warn("Refresh token error", e);
            throw new BusinessException(REFRESH_TOKEN_MISSING);
        }
    }
}
