package com.orders.order_management.exception;

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
        errorBody.put("code", ex.getErrorCode());
        errorBody.put("status", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(errorBody, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now());
        errorBody.put("error", ex.getMessage());
        errorBody.put("code", "INTERNAL_SERVER_ERROR");
        errorBody.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

        // --- CHANGE STARTS HERE ---
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody);
        // --- CHANGE ENDS HERE ---
    }
}
