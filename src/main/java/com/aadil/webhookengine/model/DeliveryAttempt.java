package com.aadil.webhookengine.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Audit record for a single HTTP delivery attempt to one subscriber.
 *
 * Table: delivery_attempts
 * Relationships:
 *   - Many DeliveryAttempts -> One WebhookEvent  (FK: event_id)
 *   - Many DeliveryAttempts -> One Subscriber    (FK: subscriber_id)
 *   - One DeliveryAttempt  -> One RetryQueueEntry (optional, mapped from RetryQueueEntry side)
 *
 * One row is created per attempt. If attempt 1 fails and a retry is scheduled,
 * the next retry creates a NEW row with attempt_number = 2. This gives a full
 * audit trail of every HTTP call made.
 */
@Entity
@Table(name = "delivery_attempts",
        indexes = {
            @Index(name = "idx_da_event_id",      columnList = "event_id"),
            @Index(name = "idx_da_subscriber_id",  columnList = "subscriber_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The event this attempt belongs to.
     * LAZY fetch — we rarely need the full event when querying attempts.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private WebhookEvent event;

    /**
     * The subscriber this attempt was delivered (or attempted to deliver) to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private Subscriber subscriber;

    /**
     * Attempt counter: 1 = first try, 2 = first retry, 3 = final retry.
     * Max 3 per (event, subscriber) pair as per spec.
     */
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    /**
     * Outcome of this specific HTTP call.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    /**
     * HTTP status code returned by the subscriber callback URL.
     * Null if the request never reached the server (connection refused, timeout, etc.).
     */
    @Column(name = "response_code")
    private Integer responseCode;

    /**
     * Timestamp of this attempt.
     */
    @CreationTimestamp
    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;
}
