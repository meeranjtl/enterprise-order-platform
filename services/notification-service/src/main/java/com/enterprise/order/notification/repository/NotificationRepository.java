package com.enterprise.order.notification.repository;

import com.enterprise.order.notification.entity.Notification;
import com.enterprise.order.notification.entity.NotificationChannel;
import com.enterprise.order.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    boolean existsByOrderIdAndTypeAndChannel(Long orderId, NotificationType type, NotificationChannel channel);
}
