package com.aadil.webhookengine.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a registered subscriber URL for a specific event type.
 *
 * Table: subscribers
 * Relationships:
 *   - One Subscriber -> Many DeliveryAttempts (cascade ALL, orphan removal)
 */
@Entity
@Table(name = "subscribers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The event type this subscriber is interested in.
     * Example: "order.created", "payment.completed"
     */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /**
     * The URL the engine will POST the event payload to.
     */
    @Column(name = "callback_url", nullable = false, length = 500)
    private String callbackUrl;

    /**
     * Optional HMAC secret key for payload signing (future use).
     * Stored but not enforced in this version (no Spring Security).
     */
    @Column(name = "secret_key", length = 255)
    private String secretKey;

    /**
     * Timestamp when this subscription was registered.
     * Auto-set on INSERT, never updated.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Soft-delete flag. When false, this subscriber is excluded from fan-out.
     * Default: true (active).
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * All delivery attempts made to this subscriber across all events.
     * mappedBy refers to the 'subscriber' field in DeliveryAttempt.
     */
    @OneToMany(mappedBy = "subscriber", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeliveryAttempt> deliveryAttempts = new ArrayList<>();
}
