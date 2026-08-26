package com.enterprise.order.shared.outbox;

import com.enterprise.order.shared.dto.BaseResponse;
import com.enterprise.order.shared.dto.OutboxEventDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only visibility into this service's transactional outbox — added for Phase 13's
 * Kafka Events UI page. Mounted under each producer service's own gateway-routed path
 * prefix (via {@code app.events.base-path}) so no new gateway route is needed; a service
 * that doesn't set the property (non-producers still carry an empty outbox_events table
 * via the shared Flyway migration) mounts harmlessly under an unrouted internal path.
 */
@RestController
@RequestMapping("${app.events.base-path:/api/v1/_internal}/events")
@RequiredArgsConstructor
public class OutboxEventController {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventMapper outboxEventMapper;

    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN')")
    // The payload column is @Lob-mapped; Postgres's JDBC driver streams it lazily,
    // and that stream is only valid while the Hibernate session is open. Without an
    // active transaction here, mapping to DTO after the repository call returns
    // throws "Unable to access lob stream" — the existing OutboxPublisher never hit
    // this because it always reads payload inside its own @Transactional method.
    @Transactional(readOnly = true)
    public ResponseEntity<BaseResponse<List<OutboxEventDTO>>> recent(
            @RequestParam(defaultValue = "50") int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        List<OutboxEventDTO> events = outboxEventRepository.findAllByOrderByCreatedAtDesc(pageable).stream()
                .map(outboxEventMapper::toDto)
                .toList();
        return ResponseEntity.ok(BaseResponse.success(events, "Recent outbox events retrieved successfully"));
    }
}
