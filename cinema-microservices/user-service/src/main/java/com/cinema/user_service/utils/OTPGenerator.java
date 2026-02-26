package com.cinema.user_service.utils;

import java.security.SecureRandom;

public class OTPGenerator {

    private static final SecureRandom random = new SecureRandom();

    public static String generateOTP(int length) {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    public static String generateOTP() {
        return generateOTP(6);
    }
}