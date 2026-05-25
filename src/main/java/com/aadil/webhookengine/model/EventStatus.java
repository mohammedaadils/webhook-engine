package com.aadil.webhookengine.model;

/**
 * Lifecycle status of an emitted event.
 *
 * PENDING   → Event saved, fan-out not yet complete.
 * COMPLETED → All subscriber deliveries succeeded.
 * PARTIAL   → At least one delivery succeeded, at least one failed after all retries.
 * FAILED    → Every subscriber delivery failed after all retries.
 */
public enum EventStatus {
    PENDING,
    COMPLETED,
    PARTIAL,
    FAILED
}
