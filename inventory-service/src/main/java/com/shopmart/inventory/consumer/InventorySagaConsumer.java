package com.shopmart.inventory.consumer;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.event.*;
import com.shopmart.inventory.config.KafkaTopicConfig;
import com.shopmart.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventorySagaConsumer {

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Saga Step 2: Handle OrderCreatedEvent -> Reserve Inventory
     */
    @KafkaListener(topics = KafkaTopicConfig.ORDER_CREATED_TOPIC, groupId = "inventory-saga-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[SAGA STEP 2 - INVENTORY RECEIVED] OrderId: {}, ProductCode: {}, Quantity: {}",
                event.getOrderId(), event.getProductCode(), event.getQuantity());

        InventoryDeductRequest request = InventoryDeductRequest.builder()
                .orderId(event.getOrderId())
                .productCode(event.getProductCode())
                .quantity(event.getQuantity())
                .build();

        InventoryResponse response = inventoryService.deductStock(request);

        if (response.isSuccess()) {
            InventoryReservedEvent reservedEvent = InventoryReservedEvent.builder()
                    .orderId(event.getOrderId())
                    .orderCode(event.getOrderCode())
                    .customerId(event.getCustomerId())
                    .productCode(event.getProductCode())
                    .quantity(event.getQuantity())
                    .totalAmount(event.getTotalAmount())
                    .forcePaymentFailure(event.getForcePaymentFailure())
                    .build();

            log.info("[SAGA STEP 2 - INVENTORY SUCCESS] Emitting InventoryReservedEvent for OrderId: {}", event.getOrderId());
            kafkaTemplate.send(KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, reservedEvent.getOrderId().toString(), reservedEvent);
        } else {
            InventoryFailedEvent failedEvent = InventoryFailedEvent.builder()
                    .orderId(event.getOrderId())
                    .orderCode(event.getOrderCode())
                    .productCode(event.getProductCode())
                    .requestedQuantity(event.getQuantity())
                    .availableStock(response.getRemainingStock())
                    .reason(response.getMessage())
                    .build();

            log.warn("[SAGA STEP 2 - INVENTORY FAILED] Emitting InventoryFailedEvent for OrderId: {}, Reason: {}",
                    event.getOrderId(), response.getMessage());
            kafkaTemplate.send(KafkaTopicConfig.INVENTORY_FAILED_TOPIC, failedEvent.getOrderId().toString(), failedEvent);
        }
    }

    /**
     * Saga Compensation / Rollback: When Payment Fails, roll back the reserved stock
     */
    @KafkaListener(topics = KafkaTopicConfig.PAYMENT_FAILED_TOPIC, groupId = "inventory-compensation-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn("[SAGA COMPENSATION TRIGGERED BY PAYMENT FAILURE] Received PaymentFailedEvent for OrderId: {}, Product: {}, Quantity: {}, Reason: '{}'",
                event.getOrderId(), event.getProductCode(), event.getQuantity(), event.getReason());

        InventoryCompensateEvent compensateEvent = InventoryCompensateEvent.builder()
                .orderId(event.getOrderId())
                .orderCode(event.getOrderCode())
                .productCode(event.getProductCode())
                .quantity(event.getQuantity())
                .reason("Compensating inventory because payment failed: " + event.getReason())
                .build();

        inventoryService.compensateStock(compensateEvent);
    }

    /**
     * Direct Inventory Compensation topic listener
     */
    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_COMPENSATE_TOPIC, groupId = "inventory-compensation-direct-group")
    public void handleDirectCompensate(InventoryCompensateEvent event) {
        log.warn("[SAGA DIRECT COMPENSATION] Restoring stock for OrderId: {}, Product: {}, Quantity: {}",
                event.getOrderId(), event.getProductCode(), event.getQuantity());
        inventoryService.compensateStock(event);
    }
}
