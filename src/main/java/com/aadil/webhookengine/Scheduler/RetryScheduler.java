package com.aadil.webhookengine.scheduler;

import com.aadil.webhookengine.model.*;
import com.aadil.webhookengine.repository.RetryQueueEntryRepository;
import com.aadil.webhookengine.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that polls the retry_queue table every 10 seconds
 * and re-delivers any webhook that is due for a retry.
 *
 * Flow per tick:
 *   1. SELECT all RetryQueueEntry where processed=false AND scheduled_at <= NOW()
 *   2. For each entry:
 *      a. Mark entry as processed = true (prevents double-fire)
 *      b. Determine next attempt number (failed attempt's number + 1)
 *      c. If next attempt > MAX_ATTEMPTS → skip (should not happen by design)
 *      d. Call webhookService.deliverToSubscriber() asynchronously
 *
 * Thread safety:
 *   - fixedDelay (not fixedRate) ensures the next tick doesn't start
 *     until the current tick finishes, avoiding concurrent scheduler runs.
 *   - Marking processed=true BEFORE dispatching the async task prevents
 *     a second scheduler tick from picking the same entry if delivery is slow.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private static final int MAX_ATTEMPTS = 3;

    private final RetryQueueEntryRepository retryQueueRepo;
    private final WebhookService            webhookService;

    /**
     * Runs every 10 seconds (fixed delay between end of last run and start of next).
     * initialDelay = 15s: give the app time to fully start before the first tick.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    @Transactional
    public void processDueRetries() {
        List<RetryQueueEntry> dueEntries =
                retryQueueRepo.findDueEntries(LocalDateTime.now());

        if (dueEntries.isEmpty()) {
            return; // Quiet tick — nothing to do
        }

        log.info("[RetryScheduler] Processing {} due entr{} at {}",
                dueEntries.size(),
                dueEntries.size() == 1 ? "y" : "ies",
                LocalDateTime.now());

        for (RetryQueueEntry entry : dueEntries) {
            DeliveryAttempt failedAttempt = entry.getDeliveryAttempt();
            int nextAttemptNumber = failedAttempt.getAttemptNumber() + 1;

            // Safety guard — should never be > MAX_ATTEMPTS by design
            if (nextAttemptNumber > MAX_ATTEMPTS) {
                log.warn("[RetryScheduler] Skipping entry id={}: would exceed max attempts",
                        entry.getId());
                entry.setProcessed(true);
                retryQueueRepo.save(entry);
                continue;
            }

            Long eventId      = failedAttempt.getEvent().getId();
            Long subscriberId = failedAttempt.getSubscriber().getId();

            log.info("[RetryScheduler] Re-delivering: eventId={} subscriberId={} attempt={}",
                    eventId, subscriberId, nextAttemptNumber);

            // Mark as processed BEFORE dispatching to prevent double-fire
            entry.setProcessed(true);
            retryQueueRepo.save(entry);

            // Dispatch the retry asynchronously (goes through Spring proxy → @Async)
            webhookService.deliverToSubscriber(eventId, subscriberId, nextAttemptNumber);
        }
    }
}
