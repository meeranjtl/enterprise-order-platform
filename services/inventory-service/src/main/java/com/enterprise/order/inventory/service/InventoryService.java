package com.enterprise.order.inventory.service;

import com.enterprise.order.inventory.dto.InventoryDTO;
import com.enterprise.order.inventory.dto.ReservationRequest;
import com.enterprise.order.inventory.dto.StockAdjustmentRequest;
import com.enterprise.order.inventory.entity.IdempotencyRecord;
import com.enterprise.order.inventory.entity.Inventory;
import com.enterprise.order.inventory.entity.InventoryTransaction;
import com.enterprise.order.inventory.entity.TransactionType;
import com.enterprise.order.inventory.repository.IdempotencyRecordRepository;
import com.enterprise.order.inventory.repository.InventoryRepository;
import com.enterprise.order.inventory.repository.InventoryTransactionRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.InternalServerException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRepository;

    @Transactional(readOnly = true)
    public InventoryDTO get(Long productId) {
        return map(find(productId), null);
    }

    public InventoryDTO reserve(ReservationRequest request, String idempotencyKey) {
        return process("RESERVE", idempotencyKey, () -> {
            Inventory inventory = locked(request.getProductId());
            if (inventory.getAvailableQuantity() < request.getQuantity()) {
                throw new BadRequestException(
                        "Insufficient inventory for product " + request.getProductId());
            }

            inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());
            return record(
                    inventory,
                    request.getOrderId(),
                    TransactionType.RESERVE,
                    request.getQuantity(),
                    "Order reservation");
        });
    }

    public InventoryDTO release(ReservationRequest request, String idempotencyKey) {
        return process("RELEASE", idempotencyKey, () -> {
            Inventory inventory = locked(request.getProductId());
            if (inventory.getReservedQuantity() < request.getQuantity()) {
                throw new BadRequestException("Cannot release more than the reserved inventory");
            }

            inventory.setReservedQuantity(inventory.getReservedQuantity() - request.getQuantity());
            inventory.setAvailableQuantity(inventory.getAvailableQuantity() + request.getQuantity());
            return record(
                    inventory,
                    request.getOrderId(),
                    TransactionType.RELEASE,
                    request.getQuantity(),
                    "Order release");
        });
    }

    public InventoryDTO adjust(StockAdjustmentRequest request, String idempotencyKey) {
        return process("ADJUST", idempotencyKey, () -> {
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(request.getProductId())
                    .orElse(Inventory.builder()
                            .productId(request.getProductId())
                            .totalQuantity(0)
                            .reservedQuantity(0)
                            .availableQuantity(0)
                            .build());

            int totalQuantity = inventory.getTotalQuantity() + request.getQuantity();
            if (totalQuantity < 0 || totalQuantity < inventory.getReservedQuantity()) {
                throw new BadRequestException("Adjustment would make available inventory negative");
            }

            inventory.setTotalQuantity(totalQuantity);
            inventory.setAvailableQuantity(totalQuantity - inventory.getReservedQuantity());
            return record(
                    inventory,
                    null,
                    TransactionType.ADJUST,
                    request.getQuantity(),
                    request.getReason());
        });
    }

    private InventoryDTO process(String operation, String idempotencyKey, Work work) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key header is required");
        }

        var previous = idempotencyRepository.findByOperationAndIdempotencyKey(operation, idempotencyKey);
        if (previous.isPresent()) {
            InventoryTransaction transaction = transactionRepository.findById(previous.get().getTransactionId())
                    .orElseThrow(() -> new InternalServerException(
                            "Idempotency record has no transaction"));
            return map(find(transaction.getProductId()), transaction);
        }

        InventoryTransaction transaction = work.execute();
        idempotencyRepository.save(IdempotencyRecord.builder()
                .operation(operation)
                .idempotencyKey(idempotencyKey)
                .transactionId(transaction.getId())
                .build());
        return map(find(transaction.getProductId()), transaction);
    }

    private InventoryTransaction record(
            Inventory inventory,
            Long orderId,
            TransactionType type,
            int quantity,
            String reason) {
        inventoryRepository.save(inventory);
        return transactionRepository.save(InventoryTransaction.builder()
                .productId(inventory.getProductId())
                .orderId(orderId)
                .type(type)
                .quantity(quantity)
                .reason(reason)
                .build());
    }

    private Inventory locked(Long productId) {
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", String.valueOf(productId)));
    }

    private Inventory find(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", String.valueOf(productId)));
    }

    private InventoryDTO map(Inventory inventory, InventoryTransaction transaction) {
        return InventoryDTO.builder()
                .productId(inventory.getProductId())
                .totalQuantity(inventory.getTotalQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .lastUpdated(inventory.getLastUpdated())
                .transactionId(transaction == null ? null : transaction.getId())
                .transactionType(transaction == null ? null : transaction.getType())
                .build();
    }

    @FunctionalInterface
    private interface Work {
        InventoryTransaction execute();
    }
}
