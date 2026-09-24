package com.shopmart.payment.service;

import com.shopmart.common.dto.PaymentRequest;
import com.shopmart.common.dto.PaymentResponse;
import com.shopmart.common.event.InventoryReservedEvent;

import java.util.List;

public interface PaymentService {
    PaymentResponse processPayment(PaymentRequest request);
    void handleSagaPayment(InventoryReservedEvent event);
    PaymentResponse getPaymentByOrderId(Long orderId);
    List<PaymentResponse> getAllPayments();
}
