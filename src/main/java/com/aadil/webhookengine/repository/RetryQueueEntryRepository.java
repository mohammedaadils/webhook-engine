package com.aadil.webhookengine.repository;

import com.aadil.webhookengine.model.RetryQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RetryQueueEntryRepository extends JpaRepository<RetryQueueEntry, Long> {

    /**
     * Core scheduler query — runs every 10 seconds.
     *
     * Fetches all unprocessed entries whose scheduled_at has elapsed.
     * JOIN FETCH eagerly loads deliveryAttempt → event + subscriber so
     * the scheduler doesn't trigger N+1 lazy loads in the retry loop.
     */
    @Query("""
        SELECT r FROM RetryQueueEntry r
        JOIN FETCH r.deliveryAttempt da
        JOIN FETCH da.event
        JOIN FETCH da.subscriber
        WHERE r.processed = false
          AND r.scheduledAt <= :now
        ORDER BY r.scheduledAt ASC
    """)
    List<RetryQueueEntry> findDueEntries(LocalDateTime now);
}
