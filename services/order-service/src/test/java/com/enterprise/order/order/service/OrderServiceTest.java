package com.enterprise.order.order.service;

import com.enterprise.order.order.client.CustomerClient;
import com.enterprise.order.order.client.ProductClient;
import com.enterprise.order.order.config.OrderPricingProperties;
import com.enterprise.order.order.dto.CreateOrderItemRequest;
import com.enterprise.order.order.dto.CreateOrderRequest;
import com.enterprise.order.order.dto.CustomerLookupDTO;
import com.enterprise.order.order.dto.OrderDTO;
import com.enterprise.order.order.dto.ProductLookupDTO;
import com.enterprise.order.order.entity.Order;
import com.enterprise.order.order.entity.OrderStatus;
import com.enterprise.order.order.mapper.OrderMapper;
import com.enterprise.order.order.repository.OrderRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import com.enterprise.order.shared.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private CustomerClient customerClient;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderPricingProperties pricingProperties;

    @Mock
    private OutboxPublisher outboxPublisher;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest request;
    private ProductLookupDTO product;
    private OrderDTO orderDTO;

    @BeforeEach
    void setUp() {
        request = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(CreateOrderItemRequest.builder().productId(10L).quantity(2).build()))
                .build();

        product = new ProductLookupDTO();
        product.setId(10L);
        product.setSku("SKU-100");
        product.setName("Wireless Keyboard");
        product.setPrice(new BigDecimal("49.99"));
        product.setStockQuantity(5);
        product.setStatus("ACTIVE");

        orderDTO = OrderDTO.builder()
                .id(100L)
                .orderNumber("ORD-20260727-ABC12345")
                .customerId(1L)
                .status("PENDING")
                .subtotal(new BigDecimal("99.98"))
                .tax(new BigDecimal("10.00"))
                .shippingCost(new BigDecimal("10.00"))
                .totalAmount(new BigDecimal("119.98"))
                .build();
    }

    @Test
    void createOrder_successCalculatesTotals() {
        when(customerClient.getCustomer(1L)).thenReturn(new CustomerLookupDTO());
        when(productClient.getProduct(10L)).thenReturn(product);
        when(pricingProperties.getTaxRate()).thenReturn(new BigDecimal("0.10"));
        when(pricingProperties.getFreeShippingThreshold()).thenReturn(new BigDecimal("100.00"));
        when(pricingProperties.getFlatShippingCost()).thenReturn(new BigDecimal("10.00"));
        when(orderRepository.existsByOrderNumber(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });
        when(orderMapper.toDTO(any(Order.class))).thenReturn(orderDTO);

        OrderDTO result = orderService.createOrder(request);

        assertEquals("PENDING", result.getStatus());
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertEquals(new BigDecimal("99.98"), saved.getSubtotal());
        assertEquals(new BigDecimal("10.00"), saved.getTax());
        assertEquals(new BigDecimal("10.00"), saved.getShippingCost());
        assertEquals(new BigDecimal("119.98"), saved.getTotalAmount());
        assertEquals(1, saved.getItems().size());
    }

    @Test
    void createOrder_freeShippingWhenSubtotalMeetsThreshold() {
        product.setPrice(new BigDecimal("60.00"));
        when(customerClient.getCustomer(1L)).thenReturn(new CustomerLookupDTO());
        when(productClient.getProduct(10L)).thenReturn(product);
        when(pricingProperties.getTaxRate()).thenReturn(new BigDecimal("0.10"));
        when(pricingProperties.getFreeShippingThreshold()).thenReturn(new BigDecimal("100.00"));
        when(orderRepository.existsByOrderNumber(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });
        when(orderMapper.toDTO(any(Order.class))).thenReturn(orderDTO);

        orderService.createOrder(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(new BigDecimal("0.00"), captor.getValue().getShippingCost());
    }

    @Test
    void createOrder_duplicateProductRejected() {
        CreateOrderRequest duplicateRequest = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(
                        CreateOrderItemRequest.builder().productId(10L).quantity(1).build(),
                        CreateOrderItemRequest.builder().productId(10L).quantity(1).build()))
                .build();

        assertThrows(BadRequestException.class, () -> orderService.createOrder(duplicateRequest));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_inactiveProductRejected() {
        product.setStatus("INACTIVE");
        when(customerClient.getCustomer(1L)).thenReturn(new CustomerLookupDTO());
        when(productClient.getProduct(10L)).thenReturn(product);

        assertThrows(BadRequestException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_insufficientStockRejected() {
        product.setStockQuantity(1);
        when(customerClient.getCustomer(1L)).thenReturn(new CustomerLookupDTO());
        when(productClient.getProduct(10L)).thenReturn(product);

        assertThrows(BadRequestException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrder_notFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrder(999L));
    }

    @Test
    void updateStatus_validTransition() {
        Order order = Order.builder().id(100L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toDTO(order)).thenReturn(OrderDTO.builder().id(100L).status("VALIDATED").build());

        OrderDTO result = orderService.updateStatus(100L, "VALIDATED");

        assertEquals("VALIDATED", result.getStatus());
        assertEquals(OrderStatus.VALIDATED, order.getStatus());
    }

    @Test
    void updateStatus_invalidTransitionRejected() {
        Order order = Order.builder().id(100L).status(OrderStatus.PENDING).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> orderService.updateStatus(100L, "COMPLETED"));
    }

    @Test
    void cancelOrder_terminalStatusRejected() {
        Order order = Order.builder().id(100L).status(OrderStatus.COMPLETED).build();
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> orderService.cancelOrder(100L));
    }

    @Test
    void getOrders_filtersByStatus() {
        Order order = Order.builder().id(100L).status(OrderStatus.PENDING).build();
        when(orderRepository.findByStatus(OrderStatus.PENDING, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderMapper.toDTO(order)).thenReturn(OrderDTO.builder().id(100L).status("PENDING").build());

        Page<OrderDTO> result = orderService.getOrders("PENDING", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }
}
