package com.shopmart.order.consumer;

import com.shopmart.common.event.InventoryFailedEvent;
import com.shopmart.common.event.PaymentCompletedEvent;
import com.shopmart.common.event.PaymentFailedEvent;
import com.shopmart.order.config.KafkaTopicConfig;
import com.shopmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaConsumer {

    private final OrderService orderService;

    /**
     * Saga Step 4 (Happy Path): Payment Completed -> Order CONFIRMED
     */
    @KafkaListener(topics = KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC, groupId = "order-saga-group")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[SAGA FINAL STEP - ORDER CONFIRMED] Received PaymentCompletedEvent for OrderId: {}, PaymentId: {}",
                event.getOrderId(), event.getPaymentId());
        orderService.confirmOrder(event.getOrderId());
    }

    /**
     * Saga Step 4 (Rollback Path): Payment Failed -> Order CANCELLED / FAILED
     */
    @KafkaListener(topics = KafkaTopicConfig.PAYMENT_FAILED_TOPIC, groupId = "order-saga-rollback-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.warn("[SAGA ROLLBACK STEP - ORDER CANCELLED] Received PaymentFailedEvent for OrderId: {}, Reason: '{}'",
                event.getOrderId(), event.getReason());
        orderService.cancelOrder(event.getOrderId(), "Payment Failed: " + event.getReason());
    }

    /**
     * Saga Step 4 (Rollback Path): Inventory Failed -> Order CANCELLED
     */
    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_FAILED_TOPIC, groupId = "order-saga-inventory-failed-group")
    public void handleInventoryFailed(InventoryFailedEvent event) {
        log.warn("[SAGA ROLLBACK STEP - INVENTORY FAILED] Received InventoryFailedEvent for OrderId: {}, Reason: '{}'",
                event.getOrderId(), event.getReason());
        orderService.cancelOrder(event.getOrderId(), "Inventory Failed: " + event.getReason());
    }
}
