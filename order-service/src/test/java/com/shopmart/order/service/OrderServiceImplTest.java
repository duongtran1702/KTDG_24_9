package com.shopmart.order.service;

import com.shopmart.common.dto.InventoryDeductRequest;
import com.shopmart.common.dto.InventoryResponse;
import com.shopmart.common.dto.OrderRequest;
import com.shopmart.common.dto.OrderResponse;
import com.shopmart.common.enums.OrderStatus;
import com.shopmart.order.client.InventoryClient;
import com.shopmart.order.entity.Order;
import com.shopmart.order.repository.OrderRepository;
import com.shopmart.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private OrderRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = OrderRequest.builder()
                .customerId("CUST-101")
                .productCode("PROD-001")
                .quantity(2)
                .price(new BigDecimal("25000000"))
                .forcePaymentFailure(false)
                .build();
    }

    @Test
    @DisplayName("Câu 2 Test: Create Order Sync via FeignClient - Success")
    void testCreateOrderSync_Success() {
        InventoryResponse inventoryResponse = InventoryResponse.builder()
                .success(true)
                .productCode("PROD-001")
                .remainingStock(98)
                .handledByInstance("8082")
                .build();

        when(inventoryClient.deductStock(any(InventoryDeductRequest.class))).thenReturn(inventoryResponse);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        OrderResponse response = orderService.createOrderSync(sampleRequest);

        assertNotNull(response);
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        assertEquals("PROD-001", response.getProductCode());
        assertEquals(2, response.getQuantity());
        verify(inventoryClient, times(1)).deductStock(any(InventoryDeductRequest.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Câu 2 Test: Resilience4j Circuit Breaker Fallback executes on service failure")
    void testCreateOrderSync_Fallback() {
        Throwable exception = new RuntimeException("Inventory service connection refused (503 Service Unavailable)");

        OrderResponse fallbackResponse = orderService.inventoryFallback(sampleRequest, exception);

        assertNotNull(fallbackResponse);
        assertEquals(OrderStatus.FAILED, fallbackResponse.getStatus());
        assertEquals("FALLBACK-REJECTED", fallbackResponse.getOrderCode());
        assertTrue(fallbackResponse.getMessage().contains("Resilience4j Circuit Breaker"));
    }

    @Test
    @DisplayName("Câu 3 Test: Create Order Saga - Initiates PENDING status and emits Kafka event")
    void testCreateOrderSaga_InitiateFlow() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderResponse response = orderService.createOrderSaga(sampleRequest);

        assertNotNull(response);
        assertEquals(OrderStatus.PENDING, response.getStatus());
        verify(kafkaTemplate, times(1)).send(eq("order-created-topic"), eq("100"), any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Câu 3 Test: Saga Rollback - Order status updated to CANCELLED on payment failure")
    void testSagaRollback_PaymentFailed() {
        Order existingOrder = Order.builder()
                .id(100L)
                .orderCode("SAGA-TEST-001")
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findById(100L)).thenReturn(Optional.of(existingOrder));

        orderService.cancelOrder(100L, "Payment failed: Insufficient funds");

        assertEquals(OrderStatus.CANCELLED, existingOrder.getStatus());
        assertEquals("Payment failed: Insufficient funds", existingOrder.getFailureReason());
        verify(orderRepository, times(1)).save(existingOrder);
    }
}
