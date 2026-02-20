package com.training.ai.interfaces.controller;

import com.training.ai.application.dto.LoginRequest;
import com.training.ai.application.dto.LoginResponse;
import com.training.ai.application.dto.RegisterRequest;
import com.training.ai.application.service.AuthService;
import com.training.ai.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功", null);
    }
    
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success("登录成功", response);
    }
    
    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success("刷新成功", response);
    }
}
