package com.chris64233.cc.sportsroster.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chris64233.cc.sportsroster.api.ErrorResponse;
import com.chris64233.cc.sportsroster.exception.BadRequestException;
import com.chris64233.cc.sportsroster.exception.IdempotencyConflictException;
import com.chris64233.cc.sportsroster.exception.RegistrationCommitConflictException;
import com.chris64233.cc.sportsroster.exception.ResourceNotFoundException;
import com.chris64233.cc.sportsroster.exception.StaleRosterVersionException;
import com.chris64233.cc.sportsroster.exception.TransferStateException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({BadRequestException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException validation
                ? validation.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(error -> error.getField() + " " + error.getDefaultMessage())
                        .orElse("请求参数不合法")
                : ex.getMessage();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", message));
    }

    @ExceptionHandler({TransferStateException.class, StaleRosterVersionException.class,
            IdempotencyConflictException.class, RegistrationCommitConflictException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", ex.getMessage()));
    }
}
