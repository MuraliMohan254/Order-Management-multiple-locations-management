package com.orders.order_management.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseCustomException.class)
    public ResponseEntity<Map<String, Object>> handleCustomException(BaseCustomException ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("error", ex.getMessage());
        errorBody.put("message", ex.getMessage());
        errorBody.put("code", ex.getErrorCode());
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(Exception ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("error", ex.getMessage());
        errorBody.put("message", ex.getMessage());
        errorBody.put("code", "UNAUTHORIZED");
        errorBody.put("status", HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(errorBody);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        String accept = request != null ? request.getHeader("Accept") : null;
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ex.getMessage() != null ? ex.getMessage() : "SSE Stream error");
        }

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("error", ex.getMessage());
        errorBody.put("message", ex.getMessage());
        errorBody.put("code", "INTERNAL_SERVER_ERROR");
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody);
    }
}
