package com.aadil.webhookengine.dto;

import com.aadil.webhookengine.model.DeliveryAttempt;
import com.aadil.webhookengine.model.DeliveryStatus;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response shape for GET /api/webhook/logs/{eventId}.
 * One object per delivery attempt row.
 */
@Getter
public class DeliveryAttemptResponse {

    private final Long id;
    private final Long eventId;
    private final Long subscriberId;
    private final String callbackUrl;
    private final int attemptNumber;
    private final DeliveryStatus status;
    private final Integer responseCode;
    private final LocalDateTime attemptedAt;

    public DeliveryAttemptResponse(DeliveryAttempt da) {
        this.id            = da.getId();
        this.eventId       = da.getEvent().getId();
        this.subscriberId  = da.getSubscriber().getId();
        this.callbackUrl   = da.getSubscriber().getCallbackUrl();
        this.attemptNumber = da.getAttemptNumber();
        this.status        = da.getStatus();
        this.responseCode  = da.getResponseCode();
        this.attemptedAt   = da.getAttemptedAt();
    }
}
