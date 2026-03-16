package com.qcom.salesanalyzer.gateway.controller;

import com.qcom.salesanalyzer.gateway.dto.LoginRequest;
import com.qcom.salesanalyzer.gateway.dto.LoginResponse;
import com.qcom.salesanalyzer.gateway.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
