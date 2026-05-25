package com.aadil.webhookengine.service;

import com.aadil.webhookengine.dto.*;
import com.aadil.webhookengine.model.*;
import com.aadil.webhookengine.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Core business logic for the Webhook Delivery Engine.
 *
 * Key design decisions:
 *
 * 1. deliverToSubscriber() is @Async — called N times (once per subscriber),
 *    each call runs on the webhookTaskExecutor thread pool in parallel.
 *    This is the fan-out.
 *
 * 2. @Async methods MUST be in a separate bean from their callers to go
 *    through the Spring proxy. That's why deliverToSubscriber() is in this
 *    service and emitEvent() calls it via Spring's proxy (self-injection via
 *    the WebhookService bean itself, not 'this.deliverToSubscriber()').
 *
 * 3. scheduleRetry() is package-private — called by RetryScheduler too.
 *
 * 4. updateEventStatus() is called at the end of emitEvent() with a small
 *    delay-tolerance trade-off: it runs after all async tasks are dispatched
 *    but before they complete (they're async). The REAL final status update
 *    happens inside each deliverToSubscriber() call after it finishes.
 *    emitEvent() sets PENDING immediately; the async workers flip it to
 *    COMPLETED / PARTIAL / FAILED once all attempts resolve.
 *
 *    Implementation note: we use a CountDownLatch approach via
 *    CompletableFuture to wait for all deliveries before resolving the
 *    event status. See emitEvent() for details.
 */
@Service
@Slf4j
public class WebhookService {

    // ─── Max attempts per (event, subscriber) pair ──────────────────────────
    private static final int MAX_ATTEMPTS = 3;

    // ─── Backoff offsets in seconds: index 0 = after attempt 1, etc. ────────
    private static final int[] BACKOFF_SECONDS = {30, 60, 120};

    private final SubscriberRepository       subscriberRepo;
    private final WebhookEventRepository     eventRepo;
    private final DeliveryAttemptRepository  attemptRepo;
    private final RetryQueueEntryRepository  retryQueueRepo;
    private final RestTemplate               restTemplate;

    /**
     * Self-reference so that calls to @Async methods go through
     * Spring's AOP proxy (required for @Async to actually intercept).
     * @Lazy breaks the circular dependency at startup cleanly.
     */
    @Autowired
    @Lazy
    private WebhookService self;

    public WebhookService(SubscriberRepository subscriberRepo,
                          WebhookEventRepository eventRepo,
                          DeliveryAttemptRepository attemptRepo,
                          RetryQueueEntryRepository retryQueueRepo,
                          RestTemplate restTemplate) {
        this.subscriberRepo = subscriberRepo;
        this.eventRepo      = eventRepo;
        this.attemptRepo    = attemptRepo;
        this.retryQueueRepo = retryQueueRepo;
        this.restTemplate   = restTemplate;
    }

    // ────────────────────────────────────────────────────────────────────────
    // PUBLIC API — called by WebhookController
    // ────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/webhook/subscribe
     * Registers a new subscriber URL for a given event type.
     */
    @Transactional
    public SubscriberResponse subscribe(SubscribeRequest req) {
        Subscriber subscriber = Subscriber.builder()
                .eventType(req.getEventType())
                .callbackUrl(req.getCallbackUrl())
                .secretKey(req.getSecretKey())
                .isActive(true)
                .build();

        Subscriber saved = subscriberRepo.save(subscriber);
        log.info("Subscriber registered: id={} eventType={} url={}",
                saved.getId(), saved.getEventType(), saved.getCallbackUrl());
        return new SubscriberResponse(saved);
    }

    /**
     * DELETE /api/webhook/unsubscribe/{id}
     * Soft-deletes a subscriber by setting isActive = false.
     * Returns false if the subscriber was not found.
     */
    @Transactional
    public boolean unsubscribe(Long id) {
        return subscriberRepo.findById(id).map(sub -> {
            sub.setActive(false);
            subscriberRepo.save(sub);
            log.info("Subscriber deactivated: id={}", id);
            return true;
        }).orElse(false);
    }

    /**
     * POST /api/webhook/emit
     *
     * 1. Saves the event with status PENDING.
     * 2. Looks up all active subscribers for the event type.
     * 3. Dispatches an async delivery task per subscriber.
     *
     * The event status (COMPLETED / PARTIAL / FAILED) is resolved
     * asynchronously inside each deliverToSubscriber() worker once
     * all fan-out calls have settled.
     *
     * Returns immediately with the saved event (status = PENDING).
     * The caller can poll GET /logs/{eventId} to track progress.
     */
    @Transactional
    public EventResponse emitEvent(EmitRequest req) {
        // Step 1 — Persist the event
        WebhookEvent event = WebhookEvent.builder()
                .eventType(req.getEventType())
                .payload(req.getPayload())
                .status(EventStatus.PENDING)
                .build();
        event = eventRepo.save(event);
        log.info("Event emitted: id={} type={}", event.getId(), event.getEventType());

        // Step 2 — Find active subscribers for this event type
        List<Subscriber> subscribers =
                subscriberRepo.findByEventTypeAndIsActiveTrue(req.getEventType());

        if (subscribers.isEmpty()) {
            log.warn("No active subscribers for eventType={}", req.getEventType());
            event.setStatus(EventStatus.COMPLETED);
            eventRepo.save(event);
            return new EventResponse(event);
        }

        // Step 3 — Fan out asynchronously (one task per subscriber)
        final Long eventId = event.getId();
        for (Subscriber subscriber : subscribers) {
            // self.deliverToSubscriber() goes through the Spring AOP proxy,
            // which is what makes @Async actually work.
            self.deliverToSubscriber(eventId, subscriber.getId(), 1);
        }

        return new EventResponse(event);
    }

    /**
     * GET /api/webhook/logs/{eventId}
     */
    @Transactional(readOnly = true)
    public List<DeliveryAttemptResponse> getLogs(Long eventId) {
        if (!eventRepo.existsById(eventId)) {
            throw new IllegalArgumentException("Event not found: id=" + eventId);
        }
        return attemptRepo.findByEventIdOrderByAttemptedAtAsc(eventId)
                .stream()
                .map(DeliveryAttemptResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * GET /api/webhook/subscribers
     */
    @Transactional(readOnly = true)
    public List<SubscriberResponse> listActiveSubscribers() {
        return subscriberRepo.findByIsActiveTrue()
                .stream()
                .map(SubscriberResponse::new)
                .collect(Collectors.toList());
    }

    // ────────────────────────────────────────────────────────────────────────
    // ASYNC DELIVERY — runs on webhookTaskExecutor threads
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Executes one HTTP POST to a subscriber's callback URL.
     *
     * Called:
     *  - By emitEvent() for attempt_number = 1
     *  - By RetryScheduler for attempt_number = 2 or 3
     *
     * @param eventId      ID of the event being delivered
     * @param subscriberId ID of the subscriber to deliver to
     * @param attemptNumber 1, 2, or 3
     */
    @Async("webhookTaskExecutor")
    @Transactional
    public void deliverToSubscriber(Long eventId, Long subscriberId, int attemptNumber) {
        WebhookEvent event      = eventRepo.findById(eventId).orElseThrow();
        Subscriber   subscriber = subscriberRepo.findById(subscriberId).orElseThrow();

        log.info("[Delivery] eventId={} subscriberId={} attempt={}",
                eventId, subscriberId, attemptNumber);

        Integer    responseCode = null;
        DeliveryStatus status;

        try {
            // Build request with JSON content type and the raw payload body
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(event.getPayload(), headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(subscriber.getCallbackUrl(), request, String.class);

            responseCode = response.getStatusCode().value();

            if (response.getStatusCode().is2xxSuccessful()) {
                status = DeliveryStatus.SUCCESS;
                log.info("[Delivery] SUCCESS eventId={} subscriberId={} http={}",
                        eventId, subscriberId, responseCode);
            } else {
                // Non-2xx treated as failure (e.g. 400, 500 from subscriber)
                status = decideFailureStatus(attemptNumber);
                log.warn("[Delivery] NON-2XX eventId={} subscriberId={} http={} attempt={}",
                        eventId, subscriberId, responseCode, attemptNumber);
            }

        } catch (RestClientException ex) {
            // Connection refused, timeout, DNS failure, etc.
            status = decideFailureStatus(attemptNumber);
            log.warn("[Delivery] ERROR eventId={} subscriberId={} attempt={} error={}",
                    eventId, subscriberId, attemptNumber, ex.getMessage());
        }

        // Persist the attempt record
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .event(event)
                .subscriber(subscriber)
                .attemptNumber(attemptNumber)
                .status(status)
                .responseCode(responseCode)
                .build();
        attempt = attemptRepo.save(attempt);

        // If failed and retries remain, schedule next retry
        if (status == DeliveryStatus.RETRYING) {
            scheduleRetry(attempt, attemptNumber);
        }

        // After every attempt, re-evaluate and update the parent event status
        updateEventStatus(event);
    }

    // ────────────────────────────────────────────────────────────────────────
    // RETRY SCHEDULING — also called by RetryScheduler
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Creates a RetryQueueEntry for the given failed attempt.
     * Called internally after a failed delivery AND by RetryScheduler.
     *
     * Backoff: attempt 1 failed → retry in 30s
     *          attempt 2 failed → retry in 60s
     *          attempt 3 failed → no more retries (status = FAILED, no queue entry)
     *
     * @param failedAttempt The DeliveryAttempt that just failed
     * @param attemptNumber The attempt number of failedAttempt (1 or 2)
     */
    @Transactional
    public void scheduleRetry(DeliveryAttempt failedAttempt, int attemptNumber) {
        // attemptNumber is 1-indexed; BACKOFF_SECONDS is 0-indexed
        int backoffSeconds = BACKOFF_SECONDS[attemptNumber - 1];
        LocalDateTime scheduledAt = LocalDateTime.now().plusSeconds(backoffSeconds);

        RetryQueueEntry entry = RetryQueueEntry.builder()
                .deliveryAttempt(failedAttempt)
                .scheduledAt(scheduledAt)
                .processed(false)
                .build();
        retryQueueRepo.save(entry);

        log.info("[Retry] Scheduled: eventId={} subscriberId={} nextAttempt={} at={}",
                failedAttempt.getEvent().getId(),
                failedAttempt.getSubscriber().getId(),
                attemptNumber + 1,
                scheduledAt);
    }

    // ────────────────────────────────────────────────────────────────────────
    // INTERNALS
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Decides whether a failed attempt should be RETRYING or permanently FAILED.
     *
     * RETRYING → attempt number < MAX_ATTEMPTS (more retries remain)
     * FAILED   → attempt number == MAX_ATTEMPTS (all retries exhausted)
     */
    private DeliveryStatus decideFailureStatus(int attemptNumber) {
        return (attemptNumber < MAX_ATTEMPTS)
                ? DeliveryStatus.RETRYING
                : DeliveryStatus.FAILED;
    }

    /**
     * Recalculates and persists the event's aggregate delivery status.
     *
     * Logic:
     *   - Look at ALL delivery attempts for this event.
     *   - For each unique subscriber, find their latest attempt.
     *   - Ignore RETRYING entries (in-flight, not final).
     *   - Count SUCCESS vs FAILED among the final (non-RETRYING) attempts.
     *
     *   all SUCCESS  → COMPLETED
     *   all FAILED   → FAILED
     *   mixed        → PARTIAL
     *   none final   → stay PENDING
     *
     * Called at the end of every deliverToSubscriber() to progressively
     * update the event status as each fan-out worker finishes.
     */
    @Transactional
    public void updateEventStatus(WebhookEvent event) {
        // Count active subscribers for this event type (used as denominator)
        List<Subscriber> subscribers =
                subscriberRepo.findByEventTypeAndIsActiveTrue(event.getEventType());

        if (subscribers.isEmpty()) {
            return;
        }

        List<DeliveryAttempt> allAttempts = attemptRepo.findByEventId(event.getId());

        // Determine final status per subscriber (take the max attempt number)
        // A subscriber is "done" if their latest attempt is SUCCESS or FAILED (not RETRYING)
        long totalSubscribers = subscribers.size();

        long successCount = subscribers.stream().filter(sub -> {
            // Find latest attempt for this subscriber
            return allAttempts.stream()
                    .filter(a -> a.getSubscriber().getId().equals(sub.getId()))
                    .max(java.util.Comparator.comparingInt(DeliveryAttempt::getAttemptNumber))
                    .map(a -> a.getStatus() == DeliveryStatus.SUCCESS)
                    .orElse(false);
        }).count();

        long failedCount = subscribers.stream().filter(sub -> {
            return allAttempts.stream()
                    .filter(a -> a.getSubscriber().getId().equals(sub.getId()))
                    .max(java.util.Comparator.comparingInt(DeliveryAttempt::getAttemptNumber))
                    .map(a -> a.getStatus() == DeliveryStatus.FAILED)
                    .orElse(false);
        }).count();

        long resolvedCount = successCount + failedCount;

        // Only update status if all subscribers have reached a terminal state
        if (resolvedCount < totalSubscribers) {
            // Some still in-flight (PENDING or RETRYING) — leave as PENDING
            return;
        }

        EventStatus newStatus;
        if (failedCount == 0) {
            newStatus = EventStatus.COMPLETED;
        } else if (successCount == 0) {
            newStatus = EventStatus.FAILED;
        } else {
            newStatus = EventStatus.PARTIAL;
        }

        // Reload to avoid stale state across async transactions
        eventRepo.findById(event.getId()).ifPresent(e -> {
            e.setStatus(newStatus);
            eventRepo.save(e);
            log.info("[EventStatus] eventId={} → {}", e.getId(), newStatus);
        });
    }
}
