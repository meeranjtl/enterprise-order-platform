package com.enterprise.order.order.controller;

import com.enterprise.order.order.dto.CreateOrderItemRequest;
import com.enterprise.order.order.dto.CreateOrderRequest;
import com.enterprise.order.order.dto.OrderDTO;
import com.enterprise.order.order.dto.OrderItemDTO;
import com.enterprise.order.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private CreateOrderRequest createRequest;
    private OrderDTO orderDTO;

    @BeforeEach
    void setUp() {
        createRequest = CreateOrderRequest.builder()
                .customerId(1L)
                .items(List.of(CreateOrderItemRequest.builder().productId(10L).quantity(2).build()))
                .build();

        orderDTO = OrderDTO.builder()
                .id(100L)
                .orderNumber("ORD-20260727-ABC12345")
                .customerId(1L)
                .status("PENDING")
                .subtotal(new BigDecimal("99.98"))
                .tax(new BigDecimal("10.00"))
                .shippingCost(new BigDecimal("10.00"))
                .totalAmount(new BigDecimal("119.98"))
                .items(List.of(OrderItemDTO.builder()
                        .id(200L)
                        .productId(10L)
                        .productSku("SKU-100")
                        .productName("Wireless Keyboard")
                        .quantity(2)
                        .unitPrice(new BigDecimal("49.99"))
                        .lineTotal(new BigDecimal("99.98"))
                        .build()))
                .build();
    }

    @Test
    void createOrder_success() throws Exception {
        when(orderService.createOrder(any())).thenReturn(orderDTO);

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.orderNumber", is("ORD-20260727-ABC12345")))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    void createOrder_validationFailure() throws Exception {
        CreateOrderRequest invalid = CreateOrderRequest.builder().customerId(1L).items(List.of()).build();

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOrder_success() throws Exception {
        when(orderService.getOrder(100L)).thenReturn(orderDTO);

        mockMvc.perform(get("/api/v1/orders/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(100)));
    }

    @Test
    void getOrderByNumber_success() throws Exception {
        when(orderService.getOrderByNumber("ORD-20260727-ABC12345")).thenReturn(orderDTO);

        mockMvc.perform(get("/api/v1/orders/number/ORD-20260727-ABC12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNumber", is("ORD-20260727-ABC12345")));
    }

    @Test
    void getOrders_success() throws Exception {
        Page<OrderDTO> page = new PageImpl<>(List.of(orderDTO));
        when(orderService.getOrders(eq("PENDING"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void getOrdersByCustomer_success() throws Exception {
        Page<OrderDTO> page = new PageImpl<>(List.of(orderDTO));
        when(orderService.getOrdersByCustomer(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    void updateStatus_success() throws Exception {
        OrderDTO updated = OrderDTO.builder().id(100L).status("VALIDATED").build();
        when(orderService.updateStatus(100L, "VALIDATED")).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/orders/100/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"VALIDATED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("VALIDATED")));
    }

    @Test
    void cancelOrder_success() throws Exception {
        doNothing().when(orderService).cancelOrder(100L);

        mockMvc.perform(delete("/api/v1/orders/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }
}
