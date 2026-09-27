package com.guftagu.identity.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.guftagu.identity.domain.ClientType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RefreshRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void missingClientType_isRejected() {
        RefreshRequest request = new RefreshRequest(null, "valid-token");
        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("clientType") && v.getMessage().contains("required"));
    }

    @Test
    void missingRefreshToken_isAllowedAtDtoLevel() {
        RefreshRequest request = new RefreshRequest(ClientType.WEB, null);
        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void blankRefreshToken_isRejected() {
        RefreshRequest request = new RefreshRequest(ClientType.WEB, "   ");
        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken") && v.getMessage().contains("blank"));
    }

    @Test
    void validRefreshToken_isAccepted() {
        RefreshRequest request = new RefreshRequest(ClientType.ANDROID, "valid-token");
        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
