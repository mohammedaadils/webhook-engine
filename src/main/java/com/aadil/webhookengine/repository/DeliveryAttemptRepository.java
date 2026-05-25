package com.aadil.webhookengine.repository;

import com.aadil.webhookengine.model.DeliveryAttempt;
import com.aadil.webhookengine.model.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {

    /**
     * GET /logs/{eventId} — full delivery audit trail for one event.
     * Returns all attempts across all subscribers, ordered oldest-first.
     */
    List<DeliveryAttempt> findByEventIdOrderByAttemptedAtAsc(Long eventId);

    /**
     * After fan-out, WebhookService calls this to determine final event status:
     *   - All SUCCESS  → COMPLETED
     *   - All FAILED   → FAILED
     *   - Mixed        → PARTIAL
     */
    List<DeliveryAttempt> findByEventId(Long eventId);

    /**
     * Used by RetryScheduler to count prior attempts for a (event, subscriber) pair
     * before deciding whether to schedule another retry or give up.
     */
    int countByEventIdAndSubscriberId(Long eventId, Long subscriberId);
}
