package com.fizu.oauth.controller;

import com.fizu.oauth.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(com.fizu.oauth.service.AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/google")
    public org.springframework.http.ResponseEntity<String> loginWithGoogle(@RequestBody java.util.Map<String, String> request) {
        String idToken = request.get("idToken");
        log.info("idToken : {}", idToken);
        String response = authService.loginWithGoogle(idToken);
        return org.springframework.http.ResponseEntity.ok(response);
    }

}
