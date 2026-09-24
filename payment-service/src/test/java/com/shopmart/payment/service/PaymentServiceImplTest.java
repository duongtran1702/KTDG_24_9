package com.shopmart.payment.service;

import com.shopmart.common.enums.PaymentStatus;
import com.shopmart.common.event.InventoryReservedEvent;
import com.shopmart.common.event.PaymentCompletedEvent;
import com.shopmart.common.event.PaymentFailedEvent;
import com.shopmart.payment.config.KafkaTopicConfig;
import com.shopmart.payment.entity.Payment;
import com.shopmart.payment.repository.PaymentRepository;
import com.shopmart.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private InventoryReservedEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = InventoryReservedEvent.builder()
                .orderId(101L)
                .orderCode("SAGA-TEST-001")
                .customerId("CUST-001")
                .productCode("PROD-001")
                .quantity(2)
                .totalAmount(new BigDecimal("30000000"))
                .forcePaymentFailure(false)
                .build();
    }

    @Test
    @DisplayName("Câu 3 Test: Saga Step 3 - Payment success emits PaymentCompletedEvent")
    void testHandleSagaPayment_Success() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(500L);
            return p;
        });

        paymentService.handleSagaPayment(sampleEvent);

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(kafkaTemplate, times(1)).send(
                eq(KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC),
                eq("101"),
                any(PaymentCompletedEvent.class)
        );
    }

    @Test
    @DisplayName("Câu 3 Test: Saga Step 3 Rollback - Payment failure emits PaymentFailedEvent to trigger compensation")
    void testHandleSagaPayment_Failure_RollbackTrigger() {
        sampleEvent.setForcePaymentFailure(true);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            assertEquals(PaymentStatus.FAILED, p.getStatus());
            assertNotNull(p.getFailureReason());
            return p;
        });

        paymentService.handleSagaPayment(sampleEvent);

        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(kafkaTemplate, times(1)).send(
                eq(KafkaTopicConfig.PAYMENT_FAILED_TOPIC),
                eq("101"),
                any(PaymentFailedEvent.class)
        );
    }
}
