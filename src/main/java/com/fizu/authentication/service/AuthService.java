package com.fizu.authentication.service;

import com.fizu.authentication.controller.dto.MessageResponse;
import com.fizu.authentication.controller.dto.RegisterResponse;
import com.fizu.authentication.controller.dto.TokenResponse;

public interface AuthService {
    TokenResponse loginWithGoogle(String idToken);

    RegisterResponse registerWithEmail(String email, String password, String username);

    TokenResponse loginWithEmail(String email, String password);

    MessageResponse verifyEmail(String email, String code);

    RegisterResponse resendVerificationCode(String email);

    MessageResponse changeUnverifiedEmail(String currentEmail, String newEmail);
}
