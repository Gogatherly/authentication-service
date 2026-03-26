package com.fizu.authentication.service;

import com.fizu.authentication.controller.dto.MessageResponse;
import com.fizu.authentication.controller.dto.RegisterResponse;
import com.fizu.authentication.controller.dto.TokenResponse;
import com.fizu.authentication.model.entity.RefreshToken;
import com.fizu.authentication.model.entity.User;
import com.fizu.authentication.model.repository.RefreshTokenRepository;
import com.fizu.authentication.model.repository.UserRepository;
import com.fizu.authentication.util.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service


public class AuthServiceImpl implements AuthService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleService googleService;
    private JwtUtil jwtUtil;
    private UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    AuthServiceImpl(GoogleService googleService, JwtUtil jwtUtil, UserRepository userRepository,
                    RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder){
        this.googleService = googleService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Value("${REFRESH_TOKEN_EXPIRATION}")
    private long refreshTokenExpiration;

    @Value("${EMAIL_VERIFICATION_EXPIRATION_DAYS:1}")
    private long emailVerificationExpirationDays;

    @Override
    public TokenResponse loginWithGoogle(String idToken) {
        GoogleIdToken.Payload payload = googleService.verify(idToken);
        if (payload == null) {
            throw new RuntimeException("Invalid ID Token");
        }
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUsername(name);
            newUser.setProvider("google");
            newUser.setEmailVerified(true);
            userRepository.save(newUser);
            return issueTokens(newUser);
        }
        return issueTokens(user.get());
    }

    @Override
    public RegisterResponse registerWithEmail(String email, String password, String username) {
        validateEmailPassword(email, password);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setProvider("email");
        user.setPassword(passwordEncoder.encode(password));
        user.setEmailVerified(false);

        String verificationToken = UUID.randomUUID().toString();
        user.setEmailVerificationToken(verificationToken);
        user.setEmailVerificationTokenExpiry(Instant.now().plus(Duration.ofDays(emailVerificationExpirationDays)));

        userRepository.save(user);
        return new RegisterResponse("Verification token created", verificationToken);
    }

    @Override
    public TokenResponse loginWithEmail(String email, String password) {
        validateEmailPassword(email, password);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (user.getProvider() != null && !"email".equals(user.getProvider())) {
            throw new RuntimeException("Use the provider linked to this account");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }
        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email not verified");
        }

        return issueTokens(user);
    }

    @Override
    public MessageResponse verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        Instant expiry = user.getEmailVerificationTokenExpiry();
        if (expiry == null || Instant.now().isAfter(expiry)) {
            throw new RuntimeException("Verification token expired");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);

        return new MessageResponse("Email verified");
    }

    private TokenResponse issueTokens(User user) {
        String token = jwtUtil.generateToken(user.getEmail());
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(refreshTokenExpiration).toLocalDate());
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(token, refreshToken.getToken());
    }

    private void validateEmailPassword(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
    }
}
