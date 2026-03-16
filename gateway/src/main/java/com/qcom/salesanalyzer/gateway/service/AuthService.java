package com.qcom.salesanalyzer.gateway.service;

import com.qcom.salesanalyzer.gateway.dto.LoginRequest;
import com.qcom.salesanalyzer.gateway.dto.LoginResponse;
import com.qcom.salesanalyzer.gateway.security.JwtTokenProvider;
import com.qcom.salesanalyzer.gateway.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        String token = tokenProvider.generateToken(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(principal.getUserId())
                .tenantId(principal.getTenantId())
                .email(principal.getEmail())
                .role(principal.getRole())
                .build();
    }
}
