package com.enterprise.order.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;
    private Long orderId;
    private String type;
    private String channel;
    private String recipient;
    private String subject;
    private String content;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
