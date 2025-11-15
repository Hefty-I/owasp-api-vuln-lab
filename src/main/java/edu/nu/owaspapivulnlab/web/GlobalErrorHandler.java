package edu.nu.owaspapivulnlab.web;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// FIX(API7): Reduced error detail exposure with proper logging
@ControllerAdvice
public class GlobalErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> all(Exception e) {
        String errorId = UUID.randomUUID().toString();
        // Log full details server-side for debugging
        logger.error("Internal error [{}]: {}", errorId, e.getMessage(), e);
        
        // Return generic error to client
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", "internal_server_error");
        errorMap.put("message", "An internal error occurred");
        errorMap.put("errorId", errorId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorMap);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> db(DataAccessException e) {
        String errorId = UUID.randomUUID().toString();
        // Log full details server-side for debugging
        logger.error("Database error [{}]: {}", errorId, e.getMessage(), e);
        
        // Return generic database error to client
        Map<String, String> errorMap = new HashMap<>();
        errorMap.put("error", "database_error");
        errorMap.put("message", "A database error occurred");
        errorMap.put("errorId", errorId);
        return ResponseEntity.status(500).body(errorMap);
    }
}
