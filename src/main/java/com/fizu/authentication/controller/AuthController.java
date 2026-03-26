package com.fizu.authentication.controller;

import com.fizu.authentication.controller.dto.EmailLoginRequest;
import com.fizu.authentication.controller.dto.EmailRegisterRequest;
import com.fizu.authentication.controller.dto.MessageResponse;
import com.fizu.authentication.controller.dto.RegisterResponse;
import com.fizu.authentication.controller.dto.TokenResponse;
import com.fizu.authentication.controller.dto.VerifyEmailRequest;
import com.fizu.authentication.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(com.fizu.authentication.service.AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/google")
    public org.springframework.http.ResponseEntity<TokenResponse> loginWithGoogle(@RequestBody java.util.Map<String, String> request) {
        String idToken = request.get("idToken");
        log.info("idToken : {}", idToken);
        TokenResponse response = authService.loginWithGoogle(idToken);
        return org.springframework.http.ResponseEntity.ok(response);
    }

    @PostMapping("/auth/email/register")
    public org.springframework.http.ResponseEntity<RegisterResponse> registerWithEmail(@RequestBody EmailRegisterRequest request) {
        RegisterResponse response = authService.registerWithEmail(request.email(), request.password(), request.username());
        return org.springframework.http.ResponseEntity.ok(response);
    }

    @PostMapping("/auth/email/login")
    public org.springframework.http.ResponseEntity<TokenResponse> loginWithEmail(@RequestBody EmailLoginRequest request) {
        TokenResponse response = authService.loginWithEmail(request.email(), request.password());
        return org.springframework.http.ResponseEntity.ok(response);
    }

    @PostMapping("/auth/email/verify")
    public org.springframework.http.ResponseEntity<MessageResponse> verifyEmail(@RequestBody VerifyEmailRequest request) {
        MessageResponse response = authService.verifyEmail(request.token());
        return org.springframework.http.ResponseEntity.ok(response);
    }
}
