package com.enterprise.order.shared.config;

import com.enterprise.order.shared.dto.BaseResponse;
import com.enterprise.order.shared.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

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
}

