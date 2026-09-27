package com.guftagu.identity.api.dto;

import com.guftagu.identity.domain.ClientType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record LogoutRequest(
        @NotNull(message = "Client type is required")
        ClientType clientType,

        @Pattern(regexp = "^.*\\S+.*$", message = "Refresh token must not be blank if provided")
        String refreshToken
) {
}
