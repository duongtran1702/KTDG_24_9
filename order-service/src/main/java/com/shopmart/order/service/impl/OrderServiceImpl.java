package com.shopmart.order.service.impl;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.dto.OrderRequest;
import com.shopmart.common.dto.OrderResponse;
import com.shopmart.common.enums.OrderStatus;
import com.shopmart.common.event.OrderCreatedEvent;
import com.shopmart.order.client.InventoryClient;
import com.shopmart.order.config.KafkaTopicConfig;
import com.shopmart.order.entity.Order;
import com.shopmart.order.repository.OrderRepository;
import com.shopmart.order.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Câu 2: Synchronous call via FeignClient with Resilience4j Circuit Breaker
     */
    @Override
    @CircuitBreaker(name = "inventoryCB", fallbackMethod = "inventoryFallback")
    @Transactional
    public OrderResponse createOrderSync(OrderRequest request) {
        log.info("[FEIGN SYNC ORDER] Calling inventory-service to verify/deduct stock for product: {}, qty: {}",
                request.getProductCode(), request.getQuantity());

        // Step 1: Call inventory-service via OpenFeign
        InventoryDeductRequest deductReq = InventoryDeductRequest.builder()
                .productCode(request.getProductCode())
                .quantity(request.getQuantity())
                .orderId(System.currentTimeMillis())
                .build();

        InventoryResponse inventoryResp = inventoryClient.deductStock(deductReq);

        if (!inventoryResp.isSuccess()) {
            throw new IllegalStateException("Inventory deduction failed: " + inventoryResp.getMessage());
        }

        BigDecimal price = request.getPrice() != null ? request.getPrice() : new BigDecimal("1000000");
        BigDecimal total = price.multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = Order.builder()
                .orderCode("SYNC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customerId(request.getCustomerId())
                .productCode(request.getProductCode())
                .quantity(request.getQuantity())
                .price(price)
                .totalAmount(total)
                .status(OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();

        Order saved = orderRepository.save(order);
        log.info("[FEIGN SYNC ORDER SUCCESS] Order saved: {}, Handled by instance: {}",
                saved.getOrderCode(), inventoryResp.getHandledByInstance());

        return mapToDto(saved, "Order created successfully via Feign Client [Handled by: " + inventoryResp.getHandledByInstance() + "]");
    }

    /**
     * Fallback method executed when inventory-service fails or Circuit Breaker is OPEN
     */
    public OrderResponse inventoryFallback(OrderRequest request, Throwable ex) {
        log.error("================================================================================");
        log.error("[CIRCUIT BREAKER FALLBACK ACTIVATED] Inventory-service call failed! Error: {}", ex.getMessage());
        log.error("Providing graceful degradation response to client.");
        log.error("================================================================================");

        BigDecimal price = request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO;
        BigDecimal total = price.multiply(BigDecimal.valueOf(request.getQuantity() != null ? request.getQuantity() : 1));

        return OrderResponse.builder()
                .orderId(-1L)
                .orderCode("FALLBACK-REJECTED")
                .customerId(request.getCustomerId())
                .productCode(request.getProductCode())
                .quantity(request.getQuantity())
                .price(price)
                .totalAmount(total)
                .status(OrderStatus.FAILED)
                .message("Inventory Service is currently unavailable. Request protected by Resilience4j Circuit Breaker: " + ex.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Câu 3: Distributed Transaction with Saga Pattern & Apache Kafka
     */
    @Override
    @Transactional
    public OrderResponse createOrderSaga(OrderRequest request) {
        BigDecimal price = request.getPrice() != null ? request.getPrice() : new BigDecimal("25000000");
        BigDecimal total = price.multiply(BigDecimal.valueOf(request.getQuantity() != null ? request.getQuantity() : 1));

        Order order = Order.builder()
                .orderCode("SAGA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .customerId(request.getCustomerId())
                .productCode(request.getProductCode())
                .quantity(request.getQuantity())
                .price(price)
                .totalAmount(total)
                .status(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Order saved = orderRepository.save(order);
        log.info("[SAGA STEP 1 - ORDER CREATED] Order placed with status PENDING: {}, OrderId: {}",
                saved.getOrderCode(), saved.getId());

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(saved.getId())
                .orderCode(saved.getOrderCode())
                .customerId(saved.getCustomerId())
                .productCode(saved.getProductCode())
                .quantity(saved.getQuantity())
                .price(saved.getPrice())
                .totalAmount(saved.getTotalAmount())
                .forcePaymentFailure(request.getForcePaymentFailure())
                .build();

        log.info("[SAGA PRODUCER] Emitting OrderCreatedEvent to Kafka topic: {}", KafkaTopicConfig.ORDER_CREATED_TOPIC);
        kafkaTemplate.send(KafkaTopicConfig.ORDER_CREATED_TOPIC, saved.getId().toString(), event);

        return mapToDto(saved, "Saga Transaction initiated. Status: PENDING (processing distributed workflow)");
    }

    @Override
    @Transactional
    public void confirmOrder(Long orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("[SAGA COMPLETE - SUCCESS] OrderId: {} status updated to CONFIRMED.", orderId);
        });
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, String reason) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason(reason);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.warn("[SAGA COMPLETE - ROLLBACK] OrderId: {} status updated to CANCELLED. Reason: '{}'", orderId, reason);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .map(o -> mapToDto(o, "Order fetched"))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(o -> mapToDto(o, "Order list"))
                .collect(Collectors.toList());
    }

    private OrderResponse mapToDto(Order o, String msg) {
        return OrderResponse.builder()
                .orderId(o.getId())
                .orderCode(o.getOrderCode())
                .customerId(o.getCustomerId())
                .productCode(o.getProductCode())
                .quantity(o.getQuantity())
                .price(o.getPrice())
                .totalAmount(o.getTotalAmount())
                .status(o.getStatus())
                .message(msg)
                .createdAt(o.getCreatedAt())
                .build();
    }
}
