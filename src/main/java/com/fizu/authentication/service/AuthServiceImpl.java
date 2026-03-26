package com.fizu.authentication.service;

import com.fizu.authentication.model.entity.RefreshToken;
import com.fizu.authentication.model.entity.User;
import com.fizu.authentication.model.repository.RefreshTokenRepository;
import com.fizu.authentication.model.repository.UserRepository;
import com.fizu.authentication.util.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service


public class AuthServiceImpl implements AuthService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleService googleService;
    private JwtUtil jwtUtil;
    private UserRepository userRepository;
    AuthServiceImpl(GoogleService googleService, JwtUtil jwtUtil, UserRepository userRepository,
                    RefreshTokenRepository refreshTokenRepository){
        this.googleService = googleService;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Value("${REFRESH_TOKEN_EXPIRATION}")
    private long refreshTokenExpiration;


    @Override
    public Map<String,String> loginWithGoogle(String idToken) {
        GoogleIdToken.Payload payload = googleService.verify(idToken);
        if (payload == null) {
            throw new RuntimeException("Invalid ID Token");
        }
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        Optional<User> user = userRepository.findByEmail(email);
        HashMap<String,String> responses = new HashMap<>();
        if (user.isEmpty()) {
//            Make new User
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUsername(name);
            newUser.setProvider("google");
            userRepository.save(newUser);

            String token = jwtUtil.generateToken(email);

//            Make refresh token
            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setUser(newUser);
            refreshToken.setExpiryDate(LocalDateTime.now().plusDays(refreshTokenExpiration).toLocalDate());
            refreshTokenRepository.save(refreshToken);

            responses.putAll(Map.of("token", token, "refreshToken", refreshToken.getToken()));
            return responses;

        }else{
            String token = jwtUtil.generateToken(email);
            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setUser(user.get());
            refreshToken.setExpiryDate(LocalDateTime.now().plusDays(refreshTokenExpiration).toLocalDate());
            refreshTokenRepository.save(refreshToken);

            responses.putAll(Map.of("token", token, "refreshToken", refreshToken.getToken()));
        }
        return responses;
    }
}
