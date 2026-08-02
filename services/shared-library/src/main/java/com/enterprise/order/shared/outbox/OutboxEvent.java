package com.enterprise.order.shared.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_published", columnList = "published"),
    @Index(name = "idx_aggregate_id", columnList = "aggregate_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "aggregate_id")
    private String aggregateId;

    @Column(nullable = false, name = "event_type")
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Lob
    private String payload;

    @Column(nullable = false, name = "kafka_topic")
    private String kafkaTopic;

    @Column(name = "kafka_key")
    private String kafkaKey;

    @Column(nullable = false, name = "published")
    @Builder.Default
    private Boolean published = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(nullable = false, name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void markAsPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
}
