package com.cinema.identity_service.services.impl;

import com.cinema.exception.BusinessException;
import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.response.RegisterCustomerResponse;
import com.cinema.identity_service.entity.User;
import com.cinema.identity_service.mapper.UserMapper;
import com.cinema.identity_service.repository.UserRepository;
import com.cinema.identity_service.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import static com.cinema.exception.ErrorCode.EMAIL_EXISTED;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    @Override
    public RegisterCustomerResponse registerCustomer(RegisterCustomerRequest registerCustomerRequest) {
        if(userRepository.existsByEmail(registerCustomerRequest.getEmail())){
            throw new BusinessException(EMAIL_EXISTED);
        }
        User user = userMapper.toUser(registerCustomerRequest);
        user.setPassword(passwordEncoder.encode(registerCustomerRequest.getPassword()));
        return null;
    }
}
