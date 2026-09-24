package com.shopmart.payment.consumer;

import com.shopmart.common.event.InventoryReservedEvent;
import com.shopmart.payment.config.KafkaTopicConfig;
import com.shopmart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSagaConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = KafkaTopicConfig.INVENTORY_RESERVED_TOPIC, groupId = "payment-saga-group")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("[KAFKA CONSUMER - PAYMENT] Received InventoryReservedEvent for OrderId: {}, Customer: {}, Amount: {}",
                event.getOrderId(), event.getCustomerId(), event.getTotalAmount());
        paymentService.handleSagaPayment(event);
    }
}
