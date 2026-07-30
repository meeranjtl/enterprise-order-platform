package com.enterprise.order.inventory.repository;

import com.enterprise.order.inventory.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByOperationAndIdempotencyKey(String operation, String idempotencyKey);
}
