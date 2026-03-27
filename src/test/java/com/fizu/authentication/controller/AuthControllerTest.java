package com.fizu.authentication.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fizu.authentication.controller.dto.RegisterResponse;
import com.fizu.authentication.exception.EmailAlreadyRegisteredException;
import com.fizu.authentication.exception.EmailNotVerifiedException;
import com.fizu.authentication.exception.GlobalExceptionHandler;
import com.fizu.authentication.exception.VerificationCodeInvalidException;
import com.fizu.authentication.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        authService = mock(AuthService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    void registerWithEmailReturnsWrappedSuccessResponse() throws Exception {
        when(authService.registerWithEmail(eq("user@example.com"), eq("password123"), eq("username")))
                .thenReturn(new RegisterResponse("Verification code created"));

        mockMvc.perform(post("/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "password", "password123",
                                "username", "username"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Verification code created"))
                .andExpect(jsonPath("$.data.message").value("Verification code created"));
    }

    @Test
    void loginWithEmailReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invalid-email",
                                "password", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").value("must be a well-formed email address"))
                .andExpect(jsonPath("$.errors.password").value("must not be blank"));
    }

    @Test
    void loginWithEmailReturnsStructuredBusinessError() throws Exception {
        when(authService.loginWithEmail(eq("user@example.com"), eq("password123")))
                .thenThrow(new EmailNotVerifiedException("Email not verified"));

        mockMvc.perform(post("/auth/email/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "password", "password123"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Email not verified"))
                .andExpect(jsonPath("$.errors").value(nullValue()));
    }

    @Test
    void registerWithEmailReturnsConflictError() throws Exception {
        when(authService.registerWithEmail(eq("user@example.com"), eq("password123"), eq("username")))
                .thenThrow(new EmailAlreadyRegisteredException("Email already registered"));

        mockMvc.perform(post("/auth/email/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "password", "password123",
                                "username", "username"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.errors").value(nullValue()));
    }

    @Test
    void verifyEmailReturnsBadRequestForInvalidVerificationCode() throws Exception {
        when(authService.verifyEmail(eq("user@example.com"), eq("123456")))
                .thenThrow(new VerificationCodeInvalidException("Invalid verification code"));

        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@example.com",
                                "code", "123456"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid verification code"))
                .andExpect(jsonPath("$.errors").value(nullValue()));
    }

    @Test
    void loginWithGoogleUsesRequestDtoValidation() throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("idToken", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.idToken").value("must not be blank"));
    }
}
