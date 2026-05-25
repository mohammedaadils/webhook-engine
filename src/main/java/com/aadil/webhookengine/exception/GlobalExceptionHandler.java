package com.aadil.webhookengine.exception;

import com.aadil.webhookengine.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response mapping.
 *
 * Every error response follows the same ApiResponse envelope:
 * {
 *   "success":   false,
 *   "message":   "human-readable error",
 *   "data":      null,
 *   "timestamp": "..."
 * }
 *
 * Handler priority (most specific first):
 *   1. MethodArgumentNotValidException  → 400  (Bean Validation failures)
 *   2. HttpMessageNotReadableException  → 400  (malformed JSON body)
 *   3. MethodArgumentTypeMismatchException → 400 (bad path variable type)
 *   4. IllegalArgumentException         → 404  (entity not found in service)
 *   5. NoResourceFoundException         → 404  (no matching route)
 *   6. Exception (catch-all)            → 500
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 1. Bean Validation failures (@Valid on request bodies) ───────────────
    //
    // Fired when a SubscribeRequest or EmitRequest fails @NotBlank / @URL etc.
    // Collects ALL field errors into one readable message so the client knows
    // exactly what's wrong without making a second request.
    //
    // Example response message:
    //   "callbackUrl: must be a valid URL; eventType: must not be blank"
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed: " + message));
    }

    // ── 2. Malformed JSON body ────────────────────────────────────────────────
    //
    // Fired when the request body is not valid JSON at all (e.g. missing closing
    // brace, unexpected token). Gives a cleaner message than the raw Jackson error.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(
            HttpMessageNotReadableException ex) {

        log.warn("Malformed JSON body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "Request body is missing or malformed. Ensure the body is valid JSON."));
    }

    // ── 3. Path variable type mismatch ───────────────────────────────────────
    //
    // Fired when a path variable can't be converted to the expected type,
    // e.g. GET /logs/abc when {eventId} expects a Long.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String message = String.format(
                "Parameter '%s' must be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        log.warn("Type mismatch: {}", message);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message));
    }

    // ── 4. Entity not found (thrown explicitly in service layer) ─────────────
    //
    // WebhookService.getLogs() throws IllegalArgumentException("Event not found: id=X")
    // when the event ID doesn't exist. Mapped to 404 here.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            IllegalArgumentException ex) {

        log.warn("Not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── 5. No matching route ─────────────────────────────────────────────────
    //
    // Fired when the request URL doesn't match any controller mapping.
    // Spring Boot 3.x throws NoResourceFoundException (replaces NoHandlerFoundException).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(
            NoResourceFoundException ex) {

        log.warn("No route found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("No endpoint found at the requested URL."));
    }

    // ── 6. Catch-all ─────────────────────────────────────────────────────────
    //
    // Any unhandled exception reaches here. Logs the full stack trace for
    // debugging but sends a generic message to the client (no internals leaked).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {

        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred. Please try again later."));
    }
}
