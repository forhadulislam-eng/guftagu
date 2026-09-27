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

class LoginRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void missingPhone_isRejected() {
        LoginRequest request = new LoginRequest(null, "validPassword123", ClientType.WEB);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber") && v.getMessage().contains("required"));
    }

    @Test
    void blankPhone_isRejected() {
        LoginRequest request = new LoginRequest("   ", "validPassword123", ClientType.WEB);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phoneNumber") && v.getMessage().contains("required"));
    }

    @Test
    void missingPassword_isRejected() {
        LoginRequest request = new LoginRequest("+14155552671", null, ClientType.WEB);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password") && v.getMessage().contains("required"));
    }

    @Test
    void shortPassword_isRejected() {
        LoginRequest request = new LoginRequest("+14155552671", "short", ClientType.WEB);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password") && v.getMessage().contains("between 10 and 128"));
    }

    @Test
    void longPassword_isRejected() {
        String longPassword = "a".repeat(129);
        LoginRequest request = new LoginRequest("+14155552671", longPassword, ClientType.WEB);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password") && v.getMessage().contains("between 10 and 128"));
    }

    @Test
    void missingClientType_isRejected() {
        LoginRequest request = new LoginRequest("+14155552671", "validPassword123", null);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("clientType") && v.getMessage().contains("required"));
    }

    @Test
    void validRequest_isAccepted() {
        LoginRequest request = new LoginRequest("+14155552671", "validPassword123", ClientType.WEB);
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }
}
