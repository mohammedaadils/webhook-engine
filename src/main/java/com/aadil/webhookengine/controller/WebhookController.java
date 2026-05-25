package com.aadil.webhookengine.controller;

import com.aadil.webhookengine.dto.*;
import com.aadil.webhookengine.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing all 5 webhook engine endpoints.
 *
 * All responses are wrapped in ApiResponse<T> for a consistent envelope:
 * {
 *   "success":   true | false,
 *   "message":   "...",
 *   "data":      { ... } | [ ... ] | null,
 *   "timestamp": "2024-..."
 * }
 *
 * HTTP status code contract:
 *   201 CREATED      → POST /subscribe         (new resource created)
 *   200 OK           → POST /emit              (event accepted, fan-out dispatched)
 *   200 OK           → GET  /logs/{eventId}    (audit log retrieved)
 *   200 OK           → GET  /subscribers       (list retrieved)
 *   200 OK           → DELETE /unsubscribe/{id}(deactivated)
 *   404 NOT FOUND    → DELETE /unsubscribe/{id} if id doesn't exist
 *   400 BAD REQUEST  → validation failures (handled by GlobalExceptionHandler)
 *   500 SERVER ERROR → unhandled exceptions    (handled by GlobalExceptionHandler)
 */
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    // ────────────────────────────────────────────────────────────────────────
    // POST /api/webhook/subscribe
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Register a subscriber URL for a specific event type.
     *
     * Request body:
     * {
     *   "eventType":   "order.created",
     *   "callbackUrl": "https://webhook.site/abc123",
     *   "secretKey":   "optional-secret"
     * }
     *
     * Response: 201 Created with the saved subscriber (no secretKey exposed).
     */
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<SubscriberResponse>> subscribe(
            @Valid @RequestBody SubscribeRequest request) {

        log.debug("POST /subscribe — eventType={} url={}",
                request.getEventType(), request.getCallbackUrl());

        SubscriberResponse response = webhookService.subscribe(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subscriber registered successfully", response));
    }

    // ────────────────────────────────────────────────────────────────────────
    // DELETE /api/webhook/unsubscribe/{id}
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Soft-delete a subscriber by ID (sets is_active = false).
     * The subscriber record is retained for audit purposes.
     *
     * Response: 200 OK if found and deactivated, 404 if not found.
     */
    @DeleteMapping("/unsubscribe/{id}")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@PathVariable Long id) {

        log.debug("DELETE /unsubscribe/{}", id);

        boolean found = webhookService.unsubscribe(id);

        if (!found) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Subscriber not found: id=" + id));
        }

        return ResponseEntity
                .ok(ApiResponse.success("Subscriber deactivated successfully"));
    }

    // ────────────────────────────────────────────────────────────────────────
    // POST /api/webhook/emit
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Emit an event and trigger async fan-out to all matching subscribers.
     *
     * Request body:
     * {
     *   "eventType": "order.created",
     *   "payload":   "{\"orderId\": 42, \"amount\": 999}"
     * }
     *
     * Returns immediately with the saved event (status = PENDING).
     * Use GET /logs/{eventId} to track delivery progress.
     *
     * Response: 200 OK with the event record.
     */
    @PostMapping("/emit")
    public ResponseEntity<ApiResponse<EventResponse>> emit(
            @Valid @RequestBody EmitRequest request) {

        log.debug("POST /emit — eventType={}", request.getEventType());

        EventResponse response = webhookService.emitEvent(request);
        return ResponseEntity
                .ok(ApiResponse.success(
                        "Event emitted successfully. Fan-out dispatched asynchronously.",
                        response));
    }

    // ────────────────────────────────────────────────────────────────────────
    // GET /api/webhook/logs/{eventId}
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Retrieve all delivery attempts for a given event ID.
     * Ordered oldest-to-newest so you can read the retry timeline top-to-bottom.
     *
     * Response: 200 OK with a list of DeliveryAttemptResponse objects.
     *           404 if the event ID doesn't exist.
     */
    @GetMapping("/logs/{eventId}")
    public ResponseEntity<ApiResponse<List<DeliveryAttemptResponse>>> getLogs(
            @PathVariable Long eventId) {

        log.debug("GET /logs/{}", eventId);

        // getLogs() throws IllegalArgumentException if eventId not found;
        // GlobalExceptionHandler maps that to 404.
        List<DeliveryAttemptResponse> logs = webhookService.getLogs(eventId);
        return ResponseEntity
                .ok(ApiResponse.success(
                        "Delivery logs retrieved: " + logs.size() + " attempt(s)",
                        logs));
    }

    // ────────────────────────────────────────────────────────────────────────
    // GET /api/webhook/subscribers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * List all currently active subscribers across all event types.
     *
     * Response: 200 OK with a list of SubscriberResponse objects.
     */
    @GetMapping("/subscribers")
    public ResponseEntity<ApiResponse<List<SubscriberResponse>>> listSubscribers() {

        log.debug("GET /subscribers");

        List<SubscriberResponse> subscribers = webhookService.listActiveSubscribers();
        return ResponseEntity
                .ok(ApiResponse.success(
                        "Active subscribers: " + subscribers.size(),
                        subscribers));
    }
}
