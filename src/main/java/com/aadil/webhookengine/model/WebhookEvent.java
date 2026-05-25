package com.aadil.webhookengine.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an emitted event that needs to be fanned out to subscribers.
 *
 * Table: events
 * Relationships:
 *   - One Event -> Many DeliveryAttempts (cascade ALL, orphan removal)
 *
 * Note: payload is stored as a raw JSON string (TEXT column).
 * No separate payload table — constraint from spec.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The type of event being emitted.
     * Must match the eventType registered by subscribers.
     * Example: "order.created"
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * The raw JSON payload provided by the emitter.
     * Stored as TEXT — MySQL TEXT supports up to ~65 KB.
     * Mapped via @Lob to ensure TEXT column type in DDL.
     */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Timestamp when the event was received via /emit.
     */
    @CreationTimestamp
    @Column(name = "emitted_at", nullable = false, updatable = false)
    private LocalDateTime emittedAt;

    /**
     * Lifecycle status of this event.
     * Updated by WebhookService after fan-out is complete.
     * Default: PENDING.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.PENDING;

    /**
     * All delivery attempts made for this event across all subscribers.
     */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryAttempt> deliveryAttempts = new ArrayList<>();
}
