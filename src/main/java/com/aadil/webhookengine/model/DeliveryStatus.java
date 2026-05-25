package com.aadil.webhookengine.model;

/**
 * Status of a single delivery attempt to one subscriber.
 *
 * SUCCESS  → HTTP 2xx received from subscriber callback URL.
 * FAILED   → Non-2xx or connection error, no further retries scheduled.
 * RETRYING → Non-2xx or error, retry has been queued.
 */
public enum DeliveryStatus {
    SUCCESS,
    FAILED,
    RETRYING
}
