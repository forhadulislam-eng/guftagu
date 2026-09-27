package com.guftagu.platform.security.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guftagu.identity.domain.ClientType;
import com.guftagu.platform.security.jwt.JwtClaims;
import com.guftagu.platform.security.jwt.JwtService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigProtectedTest.TestProtectedController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, SecurityConfigProtectedTest.TestProtectedController.class})
class SecurityConfigProtectedTest {

    @RestController
    public static class TestProtectedController {
        @GetMapping("/api/v1/status")
        public String status() {
            return "OK";
        }

        @GetMapping("/api/v1/protected")
        public String protectedEndpoint() {
            return "SECURE";
        }

        @PostMapping("/api/v1/auth/login")
        public String login() {
            return "LOGIN";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @Test
    void publicStatus_isAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk());
    }

    @Test
    void publicLogin_isAccessibleWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        when(jwtService.extractClaims(anyString())).thenThrow(new io.jsonwebtoken.MalformedJwtException("Malformed"));

        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void protectedEndpoint_withValidToken_isAccessible() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        JwtClaims claims = new JwtClaims(userId, sessionId, ClientType.WEB, "jti", Instant.now(), Instant.now().plusSeconds(600));
        when(jwtService.extractClaims("valid-token")).thenReturn(claims);

        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }
}
