package com.enterprise.order.shared.config;

import com.enterprise.order.shared.dto.BaseResponse;
import com.enterprise.order.shared.exception.ApplicationException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<BaseResponse<?>> handleApplicationException(
            ApplicationException ex, WebRequest request) {
        log.error("Application exception: {}", ex.getErrorCode(), ex);

        return ResponseEntity.status(ex.getStatusCode())
                .body(BaseResponse.error(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getDetails()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, WebRequest request) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(
                        "BAD_REQUEST",
                        "Validation failed",
                        details
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<?>> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        String details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(
                        "BAD_REQUEST",
                        "Validation failed",
                        details
                ));
    }

    /**
     * {@code @PreAuthorize} denials throw this during controller method invocation — i.e.
     * inside {@code DispatcherServlet}, already past {@code ExceptionTranslationFilter} at
     * the filter-chain level. Spring MVC's own exception resolution (this
     * {@code @RestControllerAdvice}) is what actually sees it, not the filter chain's
     * {@code accessDeniedHandler}. Without this handler it falls through to the generic
     * {@code Exception} handler below and comes back as a 500, not a 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<?>> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(BaseResponse.error(
                        "FORBIDDEN",
                        "You do not have permission to access this resource",
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<?>> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.error(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred",
                        ex.getMessage()
                ));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
