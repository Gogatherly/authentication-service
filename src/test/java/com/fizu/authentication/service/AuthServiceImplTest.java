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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private RefreshTokenRepository refreshTokenRepository;
    private JwtUtil jwtUtil;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        GoogleService googleService = mock(GoogleService.class);
        jwtUtil = mock(JwtUtil.class);
        UserRepository userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        EmailService emailService = mock(EmailService.class);

        authService = new AuthServiceImpl(
                googleService,
                jwtUtil,
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailService
        );
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
