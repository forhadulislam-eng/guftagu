package com.guftagu.platform.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class JwtAuthenticationEntryPointTest {

    @Test
    void commence_returns401WithApiErrorResponse() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Failed"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        String content = response.getContentAsString();
        assertThat(content).contains("\"code\":\"INVALID_TOKEN\"");
        assertThat(content).contains("\"message\":\"Authentication is required\"");
        assertThat(content).doesNotContain("BadCredentialsException");
        assertThat(content).doesNotContain("Failed");
    }
}
