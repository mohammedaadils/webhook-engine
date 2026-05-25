package com.aadil.webhookengine.repository;

import com.aadil.webhookengine.model.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    /**
     * Fan-out query: fetch all active subscribers for a given event type.
     * Called by WebhookService immediately after an event is emitted.
     */
    List<Subscriber> findByEventTypeAndIsActiveTrue(String eventType);

    /**
     * List endpoint: all active subscribers regardless of event type.
     */
    List<Subscriber> findByIsActiveTrue();
}
