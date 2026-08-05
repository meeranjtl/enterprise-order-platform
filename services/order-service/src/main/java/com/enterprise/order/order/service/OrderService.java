package com.enterprise.order.order.service;

import com.enterprise.order.order.client.CustomerClient;
import com.enterprise.order.order.client.ProductClient;
import com.enterprise.order.order.config.OrderPricingProperties;
import com.enterprise.order.order.dto.CreateOrderItemRequest;
import com.enterprise.order.order.dto.CreateOrderRequest;
import com.enterprise.order.order.dto.OrderDTO;
import com.enterprise.order.order.dto.ProductLookupDTO;
import com.enterprise.order.order.entity.Order;
import com.enterprise.order.order.entity.OrderItem;
import com.enterprise.order.order.entity.OrderStatus;
import com.enterprise.order.order.mapper.OrderMapper;
import com.enterprise.order.order.repository.OrderRepository;
import com.enterprise.order.shared.exception.BadRequestException;
import com.enterprise.order.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.enterprise.order.shared.outbox.OutboxPublisher;
import com.enterprise.order.shared.events.OrderCreatedEvent;
import java.util.stream.Collectors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Set<OrderStatus> TERMINAL_STATUSES = EnumSet.of(
            OrderStatus.CANCELLED, OrderStatus.FAILED, OrderStatus.SHIPPED, OrderStatus.COMPLETED);

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        // Phase 8 saga path: PENDING ->(inventory reserved)-> PAYMENT_PENDING
        //   ->(payment completed)-> PAYMENT_APPROVED / ->(payment failed)-> PAYMENT_REJECTED.
        // The manual path through VALIDATED is kept for API-driven flows.
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.VALIDATED, OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED_TRANSITIONS.put(OrderStatus.VALIDATED, EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_PENDING, EnumSet.of(OrderStatus.PAYMENT_APPROVED, OrderStatus.PAYMENT_REJECTED, OrderStatus.CANCELLED, OrderStatus.FAILED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_APPROVED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAYMENT_REJECTED, EnumSet.of(OrderStatus.FAILED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED));
        ALLOWED_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderPricingProperties pricingProperties;
    private final OutboxPublisher outboxPublisher;

    public OrderDTO createOrder(CreateOrderRequest request) {
        validateCreateRequest(request);
        customerClient.getCustomer(request.getCustomerId());

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .subtotal(ZERO)
                .tax(ZERO)
                .shippingCost(ZERO)
                .totalAmount(ZERO)
                .build();

        BigDecimal subtotal = ZERO;
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            ProductLookupDTO product = productClient.getProduct(itemRequest.getProductId());
            validateProductForOrder(product, itemRequest.getQuantity());

            BigDecimal unitPrice = money(product.getPrice());
            BigDecimal discount = ZERO;
            BigDecimal lineTotal = money(unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity())).subtract(discount));
            subtotal = subtotal.add(lineTotal);

            order.addItem(OrderItem.builder()
                    .productId(product.getId())
                    .productSku(product.getSku())
                    .productName(product.getName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .discount(discount)
                    .lineTotal(lineTotal)
                    .build());
        }

        subtotal = money(subtotal);
        BigDecimal tax = money(subtotal.multiply(pricingProperties.getTaxRate()));
        BigDecimal shipping = subtotal.compareTo(pricingProperties.getFreeShippingThreshold()) >= 0
                ? ZERO
                : money(pricingProperties.getFlatShippingCost());
        BigDecimal total = money(subtotal.add(tax).add(shipping));

        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setShippingCost(shipping);
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);

        // Store OrderCreatedEvent in outbox for reliable publishing
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(saved.getId().toString())
                .orderNumber(saved.getOrderNumber())
                .customerId(saved.getCustomerId().toString())
                .totalAmount(saved.getTotalAmount().doubleValue())
                .orderItems(saved.getItems().stream().map(i -> OrderCreatedEvent.OrderItem.builder()
                        .productId(i.getProductId().toString())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice().doubleValue())
                        .build()).collect(Collectors.toList()))
                .createdAt(java.time.LocalDateTime.now())
                .build();

        outboxPublisher.storeEvent(saved.getId().toString(), OrderCreatedEvent.EVENT_TYPE, OrderCreatedEvent.TOPIC, saved.getOrderNumber(), event);

        log.info("Created order {} for customer {}", saved.getOrderNumber(), saved.getCustomerId());
        return orderMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrder(Long id) {
        return orderMapper.toDTO(findOrder(id));
    }

    @Transactional(readOnly = true)
    public OrderDTO getOrderByNumber(String orderNumber) {
        return orderMapper.toDTO(orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber)));
    }

    @Transactional(readOnly = true)
    public Page<OrderDTO> getOrders(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return orderRepository.findAll(pageable).map(orderMapper::toDTO);
        }
        return orderRepository.findByStatus(parseStatus(status), pageable).map(orderMapper::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<OrderDTO> getOrdersByCustomer(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(orderMapper::toDTO);
    }

    public OrderDTO updateStatus(Long id, String status) {
        Order order = findOrder(id);
        OrderStatus nextStatus = parseStatus(status);
        validateTransition(order.getStatus(), nextStatus);
        order.setStatus(nextStatus);
        return orderMapper.toDTO(orderRepository.save(order));
    }

    public void cancelOrder(Long id) {
        Order order = findOrder(id);
        validateTransition(order.getStatus(), OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", String.valueOf(id)));
    }

    private void validateCreateRequest(CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("At least one order item is required");
        }
        Set<Long> productIds = request.getItems().stream()
                .map(CreateOrderItemRequest::getProductId)
                .collect(Collectors.toSet());
        if (productIds.size() != request.getItems().size()) {
            throw new BadRequestException("Duplicate products are not allowed in a single order");
        }
    }

    private void validateProductForOrder(ProductLookupDTO product, Integer quantity) {
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Product " + product.getId() + " has no valid price");
        }
        if (!"ACTIVE".equalsIgnoreCase(product.getStatus())) {
            throw new BadRequestException("Product " + product.getId() + " is not active");
        }
        if (product.getStockQuantity() == null || product.getStockQuantity() < quantity) {
            throw new BadRequestException("Insufficient stock for product " + product.getId());
        }
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        if (current == next) {
            return;
        }
        if (TERMINAL_STATUSES.contains(current)) {
            throw new BadRequestException("Order status " + current + " cannot be changed");
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new BadRequestException("Invalid order status transition from " + current + " to " + next);
        }
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadRequestException("Unsupported order status: " + status);
        }
    }

    private String generateOrderNumber() {
        String prefix = "ORD-" + LocalDate.now().format(ORDER_DATE_FORMAT) + "-";
        String orderNumber;
        do {
            orderNumber = prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (orderRepository.existsByOrderNumber(orderNumber));
        return orderNumber;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
