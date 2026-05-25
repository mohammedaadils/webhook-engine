package com.aadil.webhookengine.dto;

import com.aadil.webhookengine.model.Subscriber;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response shape for subscriber data.
 * Omits secretKey — never expose secrets in responses.
 */
@Getter
public class SubscriberResponse {

    private final Long id;
    private final String eventType;
    private final String callbackUrl;
    private final boolean active;
    private final LocalDateTime createdAt;

    public SubscriberResponse(Subscriber s) {
        this.id          = s.getId();
        this.eventType   = s.getEventType();
        this.callbackUrl = s.getCallbackUrl();
        this.active      = s.isActive();
        this.createdAt   = s.getCreatedAt();
    }
}
