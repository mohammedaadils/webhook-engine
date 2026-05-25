package com.aadil.webhookengine.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A scheduled retry entry pointing to a failed DeliveryAttempt.
 *
 * Table: retry_queue
 * Relationships:
 *   - Many RetryQueueEntries -> One DeliveryAttempt (FK: delivery_attempt_id)
 *
 * How it works:
 *   1. Delivery attempt fails → a RetryQueueEntry is created with
 *      scheduled_at = now + backoff (30 / 60 / 120 seconds).
 *   2. RetryScheduler polls every 10 seconds for entries where
 *      scheduled_at <= now AND processed = false.
 *   3. On pickup, the scheduler re-attempts delivery, creates a NEW
 *      DeliveryAttempt row, then marks this entry processed = true.
 *
 * Backoff map (attempt_number of the FAILED attempt that spawned this entry):
 *   1 → scheduled_at = failed_at + 30s
 *   2 → scheduled_at = failed_at + 60s
 *   3 → no entry created (max retries reached, mark FAILED permanently)
 */
@Entity
@Table(name = "retry_queue",
        indexes = {
            @Index(name = "idx_rq_scheduled_at", columnList = "scheduled_at"),
            @Index(name = "idx_rq_processed",    columnList = "processed")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetryQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The specific delivery attempt that failed and triggered this retry.
     * Through this we get back to both the event and the subscriber.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_attempt_id", nullable = false)
    private DeliveryAttempt deliveryAttempt;

    /**
     * The earliest wall-clock time at which this entry should be processed.
     * RetryScheduler only picks entries where scheduled_at <= NOW().
     */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /**
     * Set to true once the scheduler has picked up and processed this entry.
     * Prevents double-processing under concurrent scheduler runs.
     * Default: false.
     */
    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;
}
