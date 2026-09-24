package com.shopmart.payment.service.impl;

import com.shopmart.common.dto.PaymentRequest;
import com.shopmart.common.dto.PaymentResponse;
import com.shopmart.common.enums.PaymentStatus;
import com.shopmart.common.event.InventoryReservedEvent;
import com.shopmart.common.event.PaymentCompletedEvent;
import com.shopmart.common.event.PaymentFailedEvent;
import com.shopmart.payment.config.KafkaTopicConfig;
import com.shopmart.payment.entity.Payment;
import com.shopmart.payment.repository.PaymentRepository;
import com.shopmart.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("[DIRECT PAYMENT] Processing payment for OrderId: {}, Amount: {}", request.getOrderId(), request.getAmount());

        boolean shouldFail = Boolean.TRUE.equals(request.getForceFailure())
                || (request.getCustomerId() != null && request.getCustomerId().toUpperCase().contains("FAIL"))
                || (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("100000000")) > 0);

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .paymentDate(LocalDateTime.now())
                .build();

        if (shouldFail) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Transaction declined: Insufficient funds or test failure flag enabled");
            log.warn("[DIRECT PAYMENT FAILED] OrderId: {}, Reason: {}", request.getOrderId(), payment.getFailureReason());
        } else {
            payment.setStatus(PaymentStatus.SUCCESS);
            log.info("[DIRECT PAYMENT SUCCESS] OrderId: {}", request.getOrderId());
        }

        Payment saved = paymentRepository.save(payment);
        return mapToDto(saved);
    }

    /**
     * Saga Step 3: Handle InventoryReservedEvent -> Process Payment
     */
    @Override
    @Transactional
    public void handleSagaPayment(InventoryReservedEvent event) {
        log.info("[SAGA STEP 3 - PAYMENT RECEIVED] Processing payment for OrderId: {}, Customer: {}, Amount: {}",
                event.getOrderId(), event.getCustomerId(), event.getTotalAmount());

        boolean shouldFail = Boolean.TRUE.equals(event.getForcePaymentFailure())
                || (event.getCustomerId() != null && (event.getCustomerId().toUpperCase().contains("FAIL") || event.getCustomerId().toUpperCase().contains("ERROR")))
                || (event.getTotalAmount() != null && event.getTotalAmount().compareTo(new BigDecimal("100000000")) > 0);

        if (shouldFail) {
            String failureReason = Boolean.TRUE.equals(event.getForcePaymentFailure())
                    ? "Deliberate test failure simulated by user request"
                    : "Payment Gateway Error: Insufficient customer funds (balance insufficient)";

            Payment payment = Payment.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .amount(event.getTotalAmount())
                    .status(PaymentStatus.FAILED)
                    .failureReason(failureReason)
                    .paymentDate(LocalDateTime.now())
                    .build();
            paymentRepository.save(payment);

            log.error("[SAGA STEP 3 - PAYMENT FAILED] OrderId: {}, Amount: {}, Reason: '{}'",
                    event.getOrderId(), event.getTotalAmount(), failureReason);

            PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                    .orderId(event.getOrderId())
                    .orderCode(event.getOrderCode())
                    .productCode(event.getProductCode())
                    .quantity(event.getQuantity())
                    .amount(event.getTotalAmount())
                    .customerId(event.getCustomerId())
                    .reason(failureReason)
                    .build();

            log.warn("[SAGA ROLLBACK TRIGGER] Emitting PaymentFailedEvent to trigger compensating rollback on topics for OrderId: {}",
                    event.getOrderId());
            kafkaTemplate.send(KafkaTopicConfig.PAYMENT_FAILED_TOPIC, event.getOrderId().toString(), failedEvent);

        } else {
            Payment payment = Payment.builder()
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .amount(event.getTotalAmount())
                    .status(PaymentStatus.SUCCESS)
                    .paymentDate(LocalDateTime.now())
                    .build();
            Payment saved = paymentRepository.save(payment);

            log.info("[SAGA STEP 3 - PAYMENT SUCCESS] OrderId: {}, PaymentId: {}, Amount: {}",
                    event.getOrderId(), saved.getId(), event.getTotalAmount());

            PaymentCompletedEvent completedEvent = PaymentCompletedEvent.builder()
                    .orderId(event.getOrderId())
                    .orderCode(event.getOrderCode())
                    .paymentId(saved.getId())
                    .amount(event.getTotalAmount())
                    .customerId(event.getCustomerId())
                    .build();

            log.info("[SAGA SUCCESS] Emitting PaymentCompletedEvent to complete Saga order confirmation for OrderId: {}",
                    event.getOrderId());
            kafkaTemplate.send(KafkaTopicConfig.PAYMENT_COMPLETED_TOPIC, event.getOrderId().toString(), completedEvent);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(this::mapToDto)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByPaymentDateDesc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private PaymentResponse mapToDto(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .orderId(p.getOrderId())
                .amount(p.getAmount())
                .status(p.getStatus())
                .message(p.getFailureReason() != null ? p.getFailureReason() : "Payment processed successfully")
                .timestamp(p.getPaymentDate())
                .build();
    }
}
