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

import java.security.SecureRandom;
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
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    AuthServiceImpl(GoogleService googleService, JwtUtil jwtUtil, UserRepository userRepository,
                    RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                    EmailService emailService){
        this.googleService = googleService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Value("${REFRESH_TOKEN_EXPIRATION}")
    private long refreshTokenExpiration;

    @Value("${EMAIL_VERIFICATION_EXPIRATION_MINUTES:10}")
    private long emailVerificationExpirationMinutes;

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

        String verificationCode = generateVerificationCode();
        user.setEmailVerificationCode(verificationCode);
        user.setEmailVerificationCodeExpiry(Instant.now().plus(Duration.ofMinutes(emailVerificationExpirationMinutes)));

        userRepository.save(user);
        emailService.sendVerificationCode(email, verificationCode);
        return new RegisterResponse("Verification code created");
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
    public MessageResponse verifyEmail(String email, String code) {
        validateEmail(email);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Verification code is required");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        if (user.isEmailVerified()) {
            return new MessageResponse("Email already verified");
        }

        if (user.getEmailVerificationCode() == null || !user.getEmailVerificationCode().equals(code)) {
            throw new RuntimeException("Invalid verification code");
        }

        Instant expiry = user.getEmailVerificationCodeExpiry();
        if (expiry == null || Instant.now().isAfter(expiry)) {
            throw new RuntimeException("Verification code expired");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationCode(null);
        user.setEmailVerificationCodeExpiry(null);
        userRepository.save(user);

        return new MessageResponse("Email verified");
    }

    @Override
    public RegisterResponse resendVerificationCode(String email) {
        validateEmail(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email already verified");
        }

        String verificationCode = generateVerificationCode();
        user.setEmailVerificationCode(verificationCode);
        user.setEmailVerificationCodeExpiry(Instant.now().plus(Duration.ofMinutes(emailVerificationExpirationMinutes)));
        userRepository.save(user);

        emailService.sendVerificationCode(email, verificationCode);
        return new RegisterResponse("Verification code resent");
    }

    @Override
    public MessageResponse changeUnverifiedEmail(String currentEmail, String newEmail) {
        validateEmail(currentEmail);
        validateEmail(newEmail);

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new RuntimeException("Current email not found"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Email already verified");
        }
        if (userRepository.findByEmail(newEmail).isPresent()) {
            throw new RuntimeException("New email already registered");
        }

        user.setEmail(newEmail);
        user.setEmailVerified(false);
        String verificationCode = generateVerificationCode();
        user.setEmailVerificationCode(verificationCode);
        user.setEmailVerificationCodeExpiry(Instant.now().plus(Duration.ofMinutes(emailVerificationExpirationMinutes)));
        userRepository.save(user);

        emailService.sendVerificationCode(newEmail, verificationCode);
        return new MessageResponse("Email updated and verification code sent");
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
        validateEmail(email);
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
    }

    private String generateVerificationCode() {
        int code = secureRandom.nextInt(1_000_000);
        return String.format("%06d", code);
    }
}
