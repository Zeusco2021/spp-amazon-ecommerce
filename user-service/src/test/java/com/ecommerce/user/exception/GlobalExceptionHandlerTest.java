package com.ecommerce.user.exception;

import com.ecommerce.common.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 * Requirements: 14.4, 14.5
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    /**
     * Req 14.4: Validation errors return 400 with field-level details.
     */
    @Test
    void handleValidation_returns400WithFieldErrors() throws Exception {
        // Build a MethodArgumentNotValidException with two field errors
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must be a valid email"));
        bindingResult.addError(new FieldError("request", "password", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().fieldErrors()).containsKey("email");
        assertThat(response.getBody().fieldErrors()).containsKey("password");
        assertThat(response.getBody().fieldErrors().get("email")).isEqualTo("must be a valid email");
    }

    /**
     * Req 14.5: Generic exceptions return 500 without exposing internal details.
     */
    @Test
    void handleGeneric_returns500WithGenericMessage() {
        Exception ex = new RuntimeException("NullPointerException: internal detail that must not leak");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        // Must NOT expose internal exception message
        assertThat(response.getBody().message()).doesNotContain("NullPointerException");
        assertThat(response.getBody().message()).doesNotContain("internal detail");
        // Should return a generic safe message
        assertThat(response.getBody().message()).isNotBlank();
    }

    /**
     * Req 14.4: Single field error is included in the response.
     */
    @Test
    void handleValidation_singleFieldError_includesFieldInResponse() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "firstName", "must not be blank"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors()).containsEntry("firstName", "must not be blank");
    }
}
