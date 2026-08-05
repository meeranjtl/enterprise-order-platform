package com.enterprise.order.inventory.service;

import com.enterprise.order.inventory.dto.InventoryDTO;
import com.enterprise.order.inventory.dto.ReservationRequest;
import com.enterprise.order.inventory.entity.IdempotencyRecord;
import com.enterprise.order.inventory.entity.Inventory;
import com.enterprise.order.inventory.entity.InventoryTransaction;
import com.enterprise.order.inventory.entity.TransactionType;
import com.enterprise.order.inventory.repository.IdempotencyRecordRepository;
import com.enterprise.order.inventory.repository.InventoryRepository;
import com.enterprise.order.inventory.repository.InventoryTransactionRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.outbox.OutboxPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    @Mock
    private OutboxPublisher outboxPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void reserveMovesAvailableStockToReservedStock() {
        Inventory inventory = Inventory.builder()
                .productId(7L)
                .totalQuantity(10)
                .availableQuantity(10)
                .reservedQuantity(0)
                .build();
        when(idempotencyRepository.findByOperationAndIdempotencyKey("RESERVE", "key"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(7L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.findByProductId(7L)).thenReturn(Optional.of(inventory));
        when(transactionRepository.save(any())).thenAnswer(invocation -> {
            InventoryTransaction transaction = invocation.getArgument(0);
            transaction.setId(4L);
            return transaction;
        });

        InventoryDTO response = inventoryService.reserve(reservation(7L, 3), "key");

        assertEquals(7, response.getAvailableQuantity());
        assertEquals(3, response.getReservedQuantity());
        verify(idempotencyRepository).save(any());
    }

    @Test
    void reserveRejectsInsufficientStock() {
        Inventory inventory = Inventory.builder()
                .productId(7L)
                .totalQuantity(2)
                .availableQuantity(2)
                .reservedQuantity(0)
                .build();
        when(idempotencyRepository.findByOperationAndIdempotencyKey("RESERVE", "key"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate(7L)).thenReturn(Optional.of(inventory));

        assertThrows(BadRequestException.class, () -> inventoryService.reserve(reservation(7L, 3), "key"));
    }

    @Test
    void duplicateRequestReturnsOriginalTransaction() {
        Inventory inventory = Inventory.builder()
                .productId(7L)
                .totalQuantity(10)
                .availableQuantity(7)
                .reservedQuantity(3)
                .build();
        InventoryTransaction transaction = InventoryTransaction.builder()
                .id(4L)
                .productId(7L)
                .type(TransactionType.RESERVE)
                .quantity(3)
                .build();
        when(idempotencyRepository.findByOperationAndIdempotencyKey("RESERVE", "key"))
                .thenReturn(Optional.of(IdempotencyRecord.builder().transactionId(4L).build()));
        when(transactionRepository.findById(4L)).thenReturn(Optional.of(transaction));
        when(inventoryRepository.findByProductId(7L)).thenReturn(Optional.of(inventory));

        InventoryDTO response = inventoryService.reserve(reservation(7L, 3), "key");

        assertEquals(4L, response.getTransactionId());
        verify(inventoryRepository, never()).findByProductIdForUpdate(anyLong());
    }

    private ReservationRequest reservation(long productId, int quantity) {
        ReservationRequest request = new ReservationRequest();
        request.setProductId(productId);
        request.setOrderId(99L);
        request.setQuantity(quantity);
        return request;
    }
}
