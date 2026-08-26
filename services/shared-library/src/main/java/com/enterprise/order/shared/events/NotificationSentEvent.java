package com.enterprise.order.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Published by notification-service after a simulated email/SMS has been sent.
 * Gives downstream services (analytics, Phase 10) an audit trail of customer
 * communications.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSentEvent {

    @JsonProperty("notificationId")
    private String notificationId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("channel")
    private String channel;

    @JsonProperty("recipient")
    private String recipient;

    @JsonProperty("status")
    private String status;

    @JsonProperty("sentAt")
    private LocalDateTime sentAt;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    public static final String TOPIC = "notification-events";
    public static final String EVENT_TYPE = "NotificationSent";
}
