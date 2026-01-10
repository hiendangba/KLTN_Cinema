package com.cinema.identity_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.VerifyRequest;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.dto.response.VerifyResponse;
import com.cinema.identity_service.entity.OtpData;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.mapper.UserMapper;
import com.cinema.identity_service.repository.UserRepository;
import com.cinema.identity_service.services.UserService;
import com.cinema.identity_service.utils.OTPGenerator;
import com.cinema.identity_service.utils.VerifyTokenUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.cinema.exception.ErrorCode.*;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Qualifier("otpEncoder")
    private final PasswordEncoder otpEncoder;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String OTP_PREFIX = "otp:";
    private static final String OTP_SUBJECT_PREFIX = "otp:subject:";
    private static final int MAX_VERIFY_ATTEMPTS = 3;
    @Override
    public RegisterCustomerResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest) {
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
        System.out.println("OTP Generate:" +otp);
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

        redisTemplate.opsForValue().set(otpKey, otpData, 5, TimeUnit.MINUTES);
        return RegisterCustomerResponse.builder()
                .verifyToken(verifyToken)
                .message("Mã OTP đã được gửi đến email của bạn")
                .expiresIn(300)
                .build();
    }

    @Override
    public VerifyResponse verifyOTP(VerifyRequest verifyRequest) {
        String otpKey = OTP_PREFIX + verifyRequest.getVerifyToken();
        OtpData otpData = (OtpData) redisTemplate.opsForValue().get(otpKey);
        if (otpData == null) {
            throw new BusinessException(OTP_INVALID);
        }

        String subjectKey = OTP_SUBJECT_PREFIX + otpData.getSubject();
        String verifyToken = (String) redisTemplate.opsForValue().get(subjectKey);

        if(verifyToken == null){
            redisTemplate.delete(otpKey);
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

        switch (otpData.getPurpose()) {
            case REGISTER:
                RegisterCustomerRequest request = otpData.getRegisterCustomerRequest();
                User user = userMapper.toUser(request);
                userRepository.save(user);
                break;
            case FORGOT_PASSWORD:
                // handle reset password flow
                break;
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(OTP_SUBJECT_PREFIX + otpData.getSubject());

        return VerifyResponse.builder()
                .message("Xác thực OTP thành công")
                .build();
    }
    
}
