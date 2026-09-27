package com.guftagu.identity.application;

public record AuthenticationResult(String accessToken, String refreshToken) {
}
