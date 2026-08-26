package com.enterprise.order.shared.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox Poller runs periodically to publish pending events from the outbox table.
 * This is the "transactional outbox" pattern for exactly-once semantics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@EnableAsync
public class OutboxPoller {

    private final OutboxPublisher outboxPublisher;

    /**
     * Poll unpublished events every 5 seconds.
     * This runs async so it doesn't block the main application threads.
     */
    @Scheduled(fixedRateString = "${outbox.poll.interval:5000}")
    @Async
    public void pollAndPublishEvents() {
        try {
            outboxPublisher.publishPendingEvents();
        } catch (Exception e) {
            log.error("Error in outbox poller", e);
        }
    }
}
