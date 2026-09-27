package com.guftagu.identity.api.dto;

public record ApiErrorResponse(
        String code,
        String message
) {
}
