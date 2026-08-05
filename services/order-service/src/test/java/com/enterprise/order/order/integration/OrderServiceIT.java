package com.enterprise.order.order.integration;

import com.enterprise.order.order.client.CustomerClient;
import com.enterprise.order.order.client.ProductClient;
import com.enterprise.order.order.dto.CreateOrderItemRequest;
import com.enterprise.order.order.dto.CreateOrderRequest;
import com.enterprise.order.order.dto.CustomerLookupDTO;
import com.enterprise.order.order.dto.OrderDTO;
import com.enterprise.order.order.dto.ProductLookupDTO;
import com.enterprise.order.order.entity.Order;
import com.enterprise.order.order.entity.OrderStatus;
import com.enterprise.order.order.repository.OrderRepository;
import com.enterprise.order.order.service.OrderService;
import com.enterprise.order.shared.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("enterprise_order_order_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Mirror the Docker Compose URL (?currentSchema=orders) so unqualified table
        // names resolve in the service schema for Hibernate's ddl-auto: validate.
        registry.add("spring.datasource.url", () -> {
            String url = postgres.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=orders";
        });
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private CustomerClient customerClient;

    @MockBean
    private ProductClient productClient;

    private ProductLookupDTO product;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        CustomerLookupDTO customer = new CustomerLookupDTO();
        customer.setId(1L);
        customer.setEmail("meera@example.com");
        customer.setStatus("ACTIVE");
        when(customerClient.getCustomer(1L)).thenReturn(customer);

        product = new ProductLookupDTO();
        product.setId(10L);
        product.setSku("SKU-IT-100");
        product.setName("Wireless Keyboard");
        product.setPrice(new BigDecimal("49.99"));
        product.setStockQuantity(25);
        product.setStatus("ACTIVE");
        when(productClient.getProduct(10L)).thenReturn(product);
    }

    @Test
    @Transactional // keeps the Hibernate session open for the lazy items collection below
    void createOrder_persistsOrderAndItems() {
        OrderDTO created = orderService.createOrder(orderRequest(2));

        assertNotNull(created.getId());
        assertNotNull(created.getOrderNumber());
        assertEquals("PENDING", created.getStatus());
        assertEquals(new BigDecimal("99.98"), created.getSubtotal());
        assertEquals(new BigDecimal("10.00"), created.getTax());
        assertEquals(new BigDecimal("10.00"), created.getShippingCost());
        assertEquals(new BigDecimal("119.98"), created.getTotalAmount());
        assertEquals(1, created.getItems().size());

        Order persisted = orderRepository.findById(created.getId()).orElseThrow();
        assertEquals(1, persisted.getItems().size());
        assertEquals(OrderStatus.PENDING, persisted.getStatus());
    }

    @Test
    void createOrder_rejectsInsufficientStock() {
        product.setStockQuantity(1);

        assertThrows(BadRequestException.class, () -> orderService.createOrder(orderRequest(2)));
    }

    @Test
    void updateStatus_persistsTransition() {
        OrderDTO created = orderService.createOrder(orderRequest(1));

        OrderDTO updated = orderService.updateStatus(created.getId(), "VALIDATED");

        assertEquals("VALIDATED", updated.getStatus());
        assertEquals(OrderStatus.VALIDATED, orderRepository.findById(created.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelOrder_persistsCancelledStatus() {
        OrderDTO created = orderService.createOrder(orderRequest(1));

        orderService.cancelOrder(created.getId());

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(created.getId()).orElseThrow().getStatus());
    }

    @Test
    void getOrdersByCustomer_returnsCustomerHistory() {
        orderService.createOrder(orderRequest(1));

        Page<OrderDTO> result = orderService.getOrdersByCustomer(1L, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    private CreateOrderRequest orderRequest(int quantity) {
        return CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(CreateOrderItemRequest.builder()
                        .productId(10L)
                        .quantity(quantity)
                        .build()))
                .build();
    }
}
