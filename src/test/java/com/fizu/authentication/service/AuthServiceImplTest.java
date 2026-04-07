package com.fizu.authentication.service;

import com.fizu.authentication.controller.dto.AccessTokenResponse;
import com.fizu.authentication.controller.dto.MessageResponse;
import com.fizu.authentication.exception.InvalidRefreshTokenException;
import com.fizu.authentication.model.entity.RefreshToken;
import com.fizu.authentication.model.entity.User;
import com.fizu.authentication.model.repository.RefreshTokenRepository;
import com.fizu.authentication.model.repository.UserRepository;
import com.fizu.authentication.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private JwtUtil jwtUtil;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        GoogleService googleService = mock(GoogleService.class);
        jwtUtil = mock(JwtUtil.class);
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);

        authService = new AuthServiceImpl(
                googleService,
                jwtUtil,
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailService
        );
        ReflectionTestUtils.setField(authService, "emailVerificationExpirationMinutes", 10L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 7L);
    }

    @Test
    void registerWithEmailCreatesVerificationCodeAndPublishesMessage() {
        when(userRepository.findByEmail(eq("user@example.com"))).thenReturn(Optional.empty());
        when(passwordEncoder.encode(eq("password123"))).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.registerWithEmail("user@example.com", "password123", "username");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq("user@example.com"), codeCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("Verification code created", response.message());
        assertEquals("user@example.com", savedUser.getEmail());
        assertEquals("encoded-password", savedUser.getPassword());
        assertFalse(savedUser.isEmailVerified());
        assertNotNull(savedUser.getEmailVerificationCodeExpiry());
        assertTrue(savedUser.getEmailVerificationCodeExpiry().isAfter(Instant.now()));
        assertNotNull(codeCaptor.getValue());
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));
    }

    @Test
    void resendVerificationCodePublishesNewCode() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setEmailVerified(false);
        user.setEmailVerificationCode("111111");
        user.setEmailVerificationCodeExpiry(Instant.now().minusSeconds(60));

        when(userRepository.findByEmail(eq("user@example.com"))).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.resendVerificationCode("user@example.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq("user@example.com"), codeCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("Verification code resent", response.message());
        assertNotNull(savedUser.getEmailVerificationCodeExpiry());
        assertTrue(savedUser.getEmailVerificationCodeExpiry().isAfter(Instant.now()));
        assertNotNull(codeCaptor.getValue());
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));
    }

    @Test
    void changeUnverifiedEmailPublishesCodeToNewEmail() {
        User user = new User();
        user.setEmail("current@example.com");
        user.setUsername("username");
        user.setEmailVerified(false);

        when(userRepository.findByEmail(eq("current@example.com"))).thenReturn(Optional.of(user));
        when(userRepository.findByEmail(eq("new@example.com"))).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.changeUnverifiedEmail("current@example.com", "new@example.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationCode(eq("new@example.com"), codeCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("Email updated and verification code sent", response.message());
        assertEquals("new@example.com", savedUser.getEmail());
        assertFalse(savedUser.isEmailVerified());
        assertNotNull(codeCaptor.getValue());
        assertTrue(codeCaptor.getValue().matches("\\d{6}"));
    }

    @Test
    void logoutDeletesTokenWhenFound() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        when(refreshTokenRepository.findByToken(eq("refresh-token"))).thenReturn(Optional.of(refreshToken));

        MessageResponse response = authService.logout("refresh-token");

        assertEquals("Logout successful", response.message());
        verify(refreshTokenRepository).delete(refreshToken);
    }

    @Test
    void logoutReturnsSuccessWhenTokenNotFound() {
        when(refreshTokenRepository.findByToken(eq("missing-token"))).thenReturn(Optional.empty());

        MessageResponse response = authService.logout("missing-token");

        assertEquals("Logout successful", response.message());
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void refreshAccessTokenReturnsNewAccessTokenWhenRefreshTokenValid() {
        User user = new User();
        user.setEmail("user@example.com");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");
        refreshToken.setExpiryDate(LocalDate.now().plusDays(1));
        refreshToken.setUser(user);

        when(refreshTokenRepository.findByToken(eq("refresh-token"))).thenReturn(Optional.of(refreshToken));
        when(jwtUtil.generateToken(eq("user@example.com"))).thenReturn("new-access-token");

        AccessTokenResponse response = authService.refreshAccessToken("refresh-token");

        assertEquals("new-access-token", response.token());
        verify(jwtUtil).generateToken("user@example.com");
    }

    @Test
    void refreshAccessTokenThrowsWhenRefreshTokenNotFound() {
        when(refreshTokenRepository.findByToken(eq("unknown-token"))).thenReturn(Optional.empty());

        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refreshAccessToken("unknown-token")
        );

        assertEquals("Invalid or expired refresh token", exception.getMessage());
    }

    @Test
    void refreshAccessTokenDeletesExpiredTokenAndThrows() {
        User user = new User();
        user.setEmail("user@example.com");

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("expired-token");
        refreshToken.setExpiryDate(LocalDate.now().minusDays(1));
        refreshToken.setUser(user);

        when(refreshTokenRepository.findByToken(eq("expired-token"))).thenReturn(Optional.of(refreshToken));

        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refreshAccessToken("expired-token")
        );

        assertEquals("Invalid or expired refresh token", exception.getMessage());
        verify(refreshTokenRepository).delete(refreshToken);
    }
}
