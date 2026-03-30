package com.fizu.authentication.controller;

import com.fizu.authentication.controller.dto.AccessTokenResponse;
import com.fizu.authentication.controller.dto.ApiSuccessResponse;
import com.fizu.authentication.controller.dto.ChangeEmailRequest;
import com.fizu.authentication.controller.dto.EmailLoginRequest;
import com.fizu.authentication.controller.dto.EmailRegisterRequest;
import com.fizu.authentication.controller.dto.GoogleLoginRequest;
import com.fizu.authentication.controller.dto.LogoutRequest;
import com.fizu.authentication.controller.dto.MessageResponse;
import com.fizu.authentication.controller.dto.RefreshTokenRequest;
import com.fizu.authentication.controller.dto.RegisterResponse;
import com.fizu.authentication.controller.dto.ResendVerificationRequest;
import com.fizu.authentication.controller.dto.TokenResponse;
import com.fizu.authentication.controller.dto.VerifyEmailRequest;
import com.fizu.authentication.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/google")
    public ResponseEntity<ApiSuccessResponse<TokenResponse>> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        log.info("Received Google login request");
        TokenResponse response = authService.loginWithGoogle(request.idToken());
        return ResponseEntity.ok(ApiSuccessResponse.of("Login successful", response));
    }

    @PostMapping("/auth/email/register")
    public ResponseEntity<ApiSuccessResponse<RegisterResponse>> registerWithEmail(@Valid @RequestBody EmailRegisterRequest request) {
        RegisterResponse response = authService.registerWithEmail(request.email(), request.password(), request.username());
        return ResponseEntity.ok(ApiSuccessResponse.of(response.message(), response));
    }

    @PostMapping("/auth/email/login")
    public ResponseEntity<ApiSuccessResponse<TokenResponse>> loginWithEmail(@Valid @RequestBody EmailLoginRequest request) {
        TokenResponse response = authService.loginWithEmail(request.email(), request.password());
        return ResponseEntity.ok(ApiSuccessResponse.of("Login successful", response));
    }

    @PostMapping("/auth/email/verify")
    public ResponseEntity<ApiSuccessResponse<MessageResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        MessageResponse response = authService.verifyEmail(request.email(), request.code());
        return ResponseEntity.ok(ApiSuccessResponse.of(response.message(), response));
    }

    @PostMapping("/auth/email/resend")
    public ResponseEntity<ApiSuccessResponse<RegisterResponse>> resendVerificationCode(@Valid @RequestBody ResendVerificationRequest request) {
        RegisterResponse response = authService.resendVerificationCode(request.email());
        return ResponseEntity.ok(ApiSuccessResponse.of(response.message(), response));
    }

    @PostMapping("/auth/email/change")
    public ResponseEntity<ApiSuccessResponse<MessageResponse>> changeUnverifiedEmail(@Valid @RequestBody ChangeEmailRequest request) {
        MessageResponse response = authService.changeUnverifiedEmail(request.currentEmail(), request.newEmail());
        return ResponseEntity.ok(ApiSuccessResponse.of(response.message(), response));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiSuccessResponse<MessageResponse>> logout(@Valid @RequestBody LogoutRequest request) {
        MessageResponse response = authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiSuccessResponse.of(response.message(), response));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiSuccessResponse<AccessTokenResponse>> refreshAccessToken(@Valid @RequestBody RefreshTokenRequest request) {
        AccessTokenResponse response = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(ApiSuccessResponse.of("Token refreshed successfully", response));
    }
}
