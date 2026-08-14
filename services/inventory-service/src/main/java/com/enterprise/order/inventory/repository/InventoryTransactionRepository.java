package com.enterprise.order.inventory.repository;

import com.enterprise.order.inventory.entity.InventoryTransaction;
import com.enterprise.order.inventory.entity.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByOrderIdAndType(Long orderId, TransactionType type);
}
