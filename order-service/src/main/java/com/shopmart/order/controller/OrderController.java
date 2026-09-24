package com.shopmart.order.controller;

import com.shopmart.common.dto.OrderRequest;
import com.shopmart.common.dto.OrderResponse;
import com.shopmart.common.event.OrderCreatedEvent;
import com.shopmart.order.consumer.ReactiveOrderEventConsumer;
import com.shopmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final ReactiveOrderEventConsumer reactiveOrderConsumer;

    /**
     * Câu 2: Synchronous Feign Client call with Resilience4j Circuit Breaker
     */
    @PostMapping("/create-sync")
    public ResponseEntity<OrderResponse> createOrderSync(@RequestBody OrderRequest request) {
        log.info("[HTTP POST /api/order/create-sync] Received sync order request for product: {}", request.getProductCode());
        OrderResponse response = orderService.createOrderSync(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Câu 3: Distributed Saga Order Creation via Apache Kafka
     */
    @PostMapping("/create-saga")
    public ResponseEntity<OrderResponse> createOrderSaga(@RequestBody OrderRequest request) {
        log.info("[HTTP POST /api/order/create-saga] Initiating Saga order request for customer: {}, product: {}",
                request.getCustomerId(), request.getProductCode());
        OrderResponse response = orderService.createOrderSaga(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long orderId) {
        log.info("[HTTP GET /api/order/{}]", orderId);
        OrderResponse response = orderService.getOrderById(orderId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /**
     * Câu 3 Advanced (WebFlux): Real-time Server-Sent Events (SSE) stream of order events
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<OrderCreatedEvent> streamOrderEvents() {
        log.info("[HTTP GET /api/order/stream] Client connected to WebFlux SSE event stream");
        return reactiveOrderConsumer.getEventStream();
    }
}
