package com.aadil.webhookengine.repository;

import com.aadil.webhookengine.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    // findById(Long id) from JpaRepository is sufficient for all current use cases.
    // Add custom queries here if you later need to filter by status or event_type.
}
