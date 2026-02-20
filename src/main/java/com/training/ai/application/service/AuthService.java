package com.training.ai.application.service;

import com.training.ai.application.dto.LoginRequest;
import com.training.ai.application.dto.LoginResponse;
import com.training.ai.application.dto.RegisterRequest;
import com.training.ai.common.exception.BusinessException;
import com.training.ai.domain.user.User;
import com.training.ai.domain.user.UserRepository;
import com.training.ai.domain.user.UserStatus;
import com.training.ai.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }
        
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .phone(request.getPhone())
                .nickname(request.getNickname())
                .status(UserStatus.ACTIVE)
                .build();
        
        userRepository.save(user);
        log.info("用户注册成功: {}", user.getUsername());
    }
    
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("账号已被禁用");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());
        
        log.info("用户登录成功: {}", user.getUsername());
        
        return LoginResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
    
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("刷新令牌无效");
        }
        
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("账号已被禁用");
        }
        
        String newToken = jwtTokenProvider.generateToken(userId, username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, username);
        
        return LoginResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
}
