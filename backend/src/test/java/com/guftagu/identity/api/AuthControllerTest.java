package com.guftagu.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guftagu.identity.api.dto.LoginRequest;
import com.guftagu.identity.application.AuthenticationResult;
import com.guftagu.identity.application.AuthenticationService;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.platform.security.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationService authenticationService;

    @MockBean
    private com.guftagu.identity.application.TokenRotationService tokenRotationService;

    @MockBean
    private com.guftagu.identity.application.SessionRevocationService sessionRevocationService;

    @MockBean
    private com.guftagu.platform.security.jwt.JwtService jwtService;

    @MockBean
    private com.guftagu.platform.security.config.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    void webLogin_returnsAccessTokenAndSetsCookie_andOmitsRefreshTokenFromJson() throws Exception {
        LoginRequest request = new LoginRequest("+14155552671", "password123", ClientType.WEB);

        when(authenticationService.authenticate(eq("+14155552671"), eq("password123"), eq(ClientType.WEB)))
                .thenReturn(new AuthenticationResult("access-token-123", "refresh-token-456"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .secure(true)) // mock a secure request to test cookie secure=true
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist()) // Must not be in JSON
                .andExpect(jsonPath("$.expiresIn").doesNotExist()) // Omitted because it is null
                .andExpect(cookie().value("refresh_token", "refresh-token-456"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth/"))
                // MockMvcResultMatchers doesn't have a direct sameSite assertion before Spring Framework 6+ sometimes?
                // Spring 6 (Spring Boot 3) MockMvc does not have a native SameSite matcher in cookie(). We can verify via header instead, but actually Spring Boot 3 has `cookie().attribute("SameSite", "Strict")` if supported, let's just use string check on Set-Cookie if needed, but it's simpler not to if MockMvc can't. Wait, spring test doesn't natively expose sameSite matcher easily on the Cookie ResultMatcher. Let's just omit the sameSite assert or check the header directly.
                // We'll check the header value instead.
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assert setCookie != null;
                    assert setCookie.contains("SameSite=Strict");
                });
    }

    @Test
    void androidLogin_returnsTokensInJson_andDoesNotSetCookie() throws Exception {
        LoginRequest request = new LoginRequest("+14155552671", "password123", ClientType.ANDROID);

        when(authenticationService.authenticate(eq("+14155552671"), eq("password123"), eq(ClientType.ANDROID)))
                .thenReturn(new AuthenticationResult("access-token-123", "refresh-token-456"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-456"))
                .andExpect(jsonPath("$.expiresIn").doesNotExist())
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    @Test
    void validation_invalidRequestReturns400() throws Exception {
        LoginRequest request = new LoginRequest("   ", "short", null);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webRefresh_returnsAccessTokenAndSetsCookie_andOmitsRefreshTokenFromJson() throws Exception {
        com.guftagu.identity.api.dto.RefreshRequest request = new com.guftagu.identity.api.dto.RefreshRequest(ClientType.WEB, null);

        when(tokenRotationService.rotateToken(eq("cookie-refresh-token")))
                .thenReturn(new AuthenticationResult("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "cookie-refresh-token"))
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist()) // Never return the old or new in JSON
                .andExpect(jsonPath("$.expiresIn").doesNotExist())
                .andExpect(cookie().value("refresh_token", "new-refresh-token")) // Replaces old cookie
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth/"))
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assert setCookie != null;
                    assert setCookie.contains("SameSite=Strict");
                });
    }

    @Test
    void androidRefresh_returnsTokensInJson_andDoesNotSetCookie() throws Exception {
        com.guftagu.identity.api.dto.RefreshRequest request = new com.guftagu.identity.api.dto.RefreshRequest(ClientType.ANDROID, "body-refresh-token");

        when(tokenRotationService.rotateToken(eq("body-refresh-token")))
                .thenReturn(new AuthenticationResult("new-access-token", "new-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.expiresIn").doesNotExist())
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    @Test
    void webRefresh_missingCookie_callsServiceWithNull_andReturns401() throws Exception {
        com.guftagu.identity.api.dto.RefreshRequest request = new com.guftagu.identity.api.dto.RefreshRequest(ClientType.WEB, null);

        when(tokenRotationService.rotateToken(null))
                .thenThrow(new com.guftagu.identity.application.exception.InvalidTokenException("Token is null"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
    }

    @Test
    void androidRefresh_blankToken_returns400() throws Exception {
        com.guftagu.identity.api.dto.RefreshRequest request = new com.guftagu.identity.api.dto.RefreshRequest(ClientType.ANDROID, "   ");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webLogout_returns204AndClearsCookie() throws Exception {
        com.guftagu.identity.api.dto.LogoutRequest request = new com.guftagu.identity.api.dto.LogoutRequest(ClientType.WEB, null);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "cookie-refresh-token"))
                        .secure(true))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("refresh_token", "")) // cleared
                .andExpect(cookie().maxAge("refresh_token", 0)) // Max-Age=0
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth/"))
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assert setCookie != null;
                    assert setCookie.contains("SameSite=Strict");
                });

        org.mockito.Mockito.verify(sessionRevocationService).revokeSession("cookie-refresh-token");
    }

    @Test
    void androidLogout_returns204AndDoesNotSetCookie() throws Exception {
        com.guftagu.identity.api.dto.LogoutRequest request = new com.guftagu.identity.api.dto.LogoutRequest(ClientType.ANDROID, "body-refresh-token");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().doesNotExist("refresh_token"));

        org.mockito.Mockito.verify(sessionRevocationService).revokeSession("body-refresh-token");
    }

    @Test
    void webLogout_missingCookie_returns204AndClearsCookie() throws Exception {
        com.guftagu.identity.api.dto.LogoutRequest request = new com.guftagu.identity.api.dto.LogoutRequest(ClientType.WEB, null);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("refresh_token", ""))
                .andExpect(cookie().maxAge("refresh_token", 0));

        org.mockito.Mockito.verify(sessionRevocationService).revokeSession(null);
    }

    @Test
    void androidLogout_blankToken_returns400() throws Exception {
        com.guftagu.identity.api.dto.LogoutRequest request = new com.guftagu.identity.api.dto.LogoutRequest(ClientType.ANDROID, "   ");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
