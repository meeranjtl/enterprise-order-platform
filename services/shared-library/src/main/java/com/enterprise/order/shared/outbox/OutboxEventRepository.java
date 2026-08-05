package com.enterprise.order.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.published = false ORDER BY o.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents();

    @Query("SELECT o FROM OutboxEvent o WHERE o.aggregateId = ?1 ORDER BY o.createdAt DESC LIMIT 1")
    OutboxEvent findLatestByAggregateId(String aggregateId);
}
