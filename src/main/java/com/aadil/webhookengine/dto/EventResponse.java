package com.aadil.webhookengine.dto;

import com.aadil.webhookengine.model.EventStatus;
import com.aadil.webhookengine.model.WebhookEvent;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response shape returned by POST /api/webhook/emit.
 */
@Getter
public class EventResponse {

    private final Long id;
    private final String eventType;
    private final String payload;
    private final EventStatus status;
    private final LocalDateTime emittedAt;

    public EventResponse(WebhookEvent e) {
        this.id        = e.getId();
        this.eventType = e.getEventType();
        this.payload   = e.getPayload();
        this.status    = e.getStatus();
        this.emittedAt = e.getEmittedAt();
    }
}
