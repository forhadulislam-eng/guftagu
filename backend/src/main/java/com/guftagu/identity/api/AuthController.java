package com.guftagu.identity.api;

import com.guftagu.identity.api.dto.LoginRequest;
import com.guftagu.identity.api.dto.LoginResponse;
import com.guftagu.identity.api.dto.LogoutRequest;
import com.guftagu.identity.api.dto.RefreshRequest;
import com.guftagu.identity.application.AuthenticationResult;
import com.guftagu.identity.application.AuthenticationService;
import com.guftagu.identity.application.SessionRevocationService;
import com.guftagu.identity.application.TokenRotationService;
import com.guftagu.identity.domain.ClientType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final TokenRotationService tokenRotationService;
    private final SessionRevocationService sessionRevocationService;

    public AuthController(
            AuthenticationService authenticationService,
            TokenRotationService tokenRotationService,
            SessionRevocationService sessionRevocationService) {
        this.authenticationService = authenticationService;
        this.tokenRotationService = tokenRotationService;
        this.sessionRevocationService = sessionRevocationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthenticationResult result = authenticationService.authenticate(
                request.phoneNumber(),
                request.password(),
                request.clientType()
        );

        if (request.clientType() == ClientType.WEB) {
            ResponseCookie cookie = ResponseCookie.from("refresh_token", result.refreshToken())
                    .httpOnly(true)
                    .secure(httpRequest.isSecure())
                    .path("/api/v1/auth/")
                    .sameSite("Strict")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new LoginResponse(result.accessToken(), null, null));
        }

        return ResponseEntity.ok(
                new LoginResponse(result.accessToken(), result.refreshToken(), null)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            HttpServletRequest httpRequest) {

        String rawToken = request.clientType() == ClientType.WEB
                ? cookieToken
                : request.refreshToken();

        AuthenticationResult result = tokenRotationService.rotateToken(rawToken);

        if (request.clientType() == ClientType.WEB) {
            ResponseCookie cookie = ResponseCookie.from("refresh_token", result.refreshToken())
                    .httpOnly(true)
                    .secure(httpRequest.isSecure())
                    .path("/api/v1/auth/")
                    .sameSite("Strict")
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(new LoginResponse(result.accessToken(), null, null));
        }

        return ResponseEntity.ok(
                new LoginResponse(result.accessToken(), result.refreshToken(), null)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @CookieValue(name = "refresh_token", required = false) String cookieToken,
            HttpServletRequest httpRequest) {

        String rawToken = request.clientType() == ClientType.WEB
                ? cookieToken
                : request.refreshToken();

        sessionRevocationService.revokeSession(rawToken);

        if (request.clientType() == ClientType.WEB) {
            ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                    .maxAge(0)
                    .httpOnly(true)
                    .secure(httpRequest.isSecure())
                    .path("/api/v1/auth/")
                    .sameSite("Strict")
                    .build();

            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .build();
        }

        return ResponseEntity.noContent().build();
    }
}
