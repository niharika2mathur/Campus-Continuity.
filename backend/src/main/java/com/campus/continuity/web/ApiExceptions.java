package com.campus.continuity.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.time.Instant;
import java.util.*;

public final class ApiExceptions {
    private ApiExceptions() {}

    public static class NotFoundException extends RuntimeException { public NotFoundException(String m) { super(m); } }
    public static class ConflictException extends RuntimeException { public ConflictException(String m) { super(m); } }

    public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors, String path) {}

    @RestControllerAdvice
    public static class Handler {
        private static final Logger log = LoggerFactory.getLogger(Handler.class);

        private ResponseEntity<ApiError> build(HttpStatus s, String msg, Map<String, String> f, HttpServletRequest r) {
            return ResponseEntity.status(s).body(new ApiError(Instant.now(), s.value(), s.getReasonPhrase(), msg, f, r.getRequestURI()));
        }
        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
            Map<String, String> f = new LinkedHashMap<>();
            e.getBindingResult().getFieldErrors().forEach(x -> f.putIfAbsent(x.getField(), x.getDefaultMessage()));
            return build(HttpStatus.BAD_REQUEST, "Validation failed for " + f.size() + " field(s)", f, r);
        }
        @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
        ResponseEntity<ApiError> badInput(Exception e, HttpServletRequest r) { return build(HttpStatus.BAD_REQUEST, e instanceof IllegalArgumentException ? e.getMessage() : "Malformed request body or parameter", null, r); }
        @ExceptionHandler(NotFoundException.class)
        ResponseEntity<ApiError> notFound(NotFoundException e, HttpServletRequest r) { return build(HttpStatus.NOT_FOUND, e.getMessage(), null, r); }
        @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
        ResponseEntity<ApiError> conflict(Exception e, HttpServletRequest r) {
            return build(HttpStatus.CONFLICT, e instanceof ConflictException ? e.getMessage() : "A record with the same unique value already exists", null, r);
        }
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        ResponseEntity<ApiError> tooBig(Exception e, HttpServletRequest r) { return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is larger than 2 MB", null, r); }
        @ExceptionHandler(Exception.class)
        ResponseEntity<ApiError> other(Exception e, HttpServletRequest r) {
            log.error("Unhandled error", e);
            return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", null, r);
        }
    }
}
