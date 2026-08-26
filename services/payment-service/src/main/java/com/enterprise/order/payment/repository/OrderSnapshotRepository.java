package com.enterprise.order.payment.repository;

import com.enterprise.order.payment.entity.OrderSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {
}
