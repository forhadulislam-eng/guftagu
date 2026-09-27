package com.guftagu.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guftagu.identity.api.dto.LoginRequest;
import com.guftagu.identity.application.AuthenticationService;
import com.guftagu.identity.application.SessionRevocationService;
import com.guftagu.identity.application.TokenRotationService;
import com.guftagu.identity.application.exception.InvalidCredentialsException;
import com.guftagu.identity.application.exception.InvalidTokenException;
import com.guftagu.identity.application.exception.ReplayDetectedException;
import com.guftagu.identity.domain.ClientType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(com.guftagu.platform.security.config.SecurityConfig.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private TokenRotationService tokenRotationService;

    @MockBean
    private SessionRevocationService sessionRevocationService;

    @MockBean
    private com.guftagu.platform.security.jwt.JwtService jwtService;

    @MockBean
    private com.guftagu.platform.security.config.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    void invalidCredentials_returns401AndGenericMessage() throws Exception {
        LoginRequest request = new LoginRequest("+14155552671", "wrongpassword", ClientType.WEB);
        when(authenticationService.authenticate(any(), any(), any())).thenThrow(new InvalidCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid phone number or password"));
    }

    @Test
    void invalidToken_returns401AndGenericMessage() throws Exception {
        com.guftagu.identity.api.dto.RefreshRequest request = new com.guftagu.identity.api.dto.RefreshRequest(ClientType.ANDROID, "bad-token");
        when(tokenRotationService.rotateToken(any())).thenThrow(new InvalidTokenException("Invalid refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    @Test
    void replayDetected_returns401AndGenericMessageWithoutLeakingReplayState() throws Exception {
        com.guftagu.identity.api.dto.RefreshRequest request = new com.guftagu.identity.api.dto.RefreshRequest(ClientType.ANDROID, "consumed-token");
        when(tokenRotationService.rotateToken(any())).thenThrow(new ReplayDetectedException("Refresh token replay detected"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    @Test
    void validationError_returns400AndGenericMessage() throws Exception {
        // Missing required fields will trigger MethodArgumentNotValidException
        LoginRequest request = new LoginRequest(null, null, null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @Test
    void malformedJson_returns400AndGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\": \"+14155552671\", \"password\": \"missing-quote}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void unexpectedException_returns500AndGenericMessage() throws Exception {
        LoginRequest request = new LoginRequest("+14155552671", "password123", ClientType.WEB);
        when(authenticationService.authenticate(any(), any(), any())).thenThrow(new RuntimeException("Database down"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
