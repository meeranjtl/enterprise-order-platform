package com.enterprise.order.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventDTO {
    private Long id;
    private String aggregateId;
    private String eventType;
    private String kafkaTopic;
    private String kafkaKey;
    private String payload;
    private Boolean published;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
