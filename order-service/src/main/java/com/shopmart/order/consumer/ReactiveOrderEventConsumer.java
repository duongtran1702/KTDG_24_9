package com.shopmart.order.consumer;

import com.shopmart.common.event.OrderCreatedEvent;
import com.shopmart.order.config.KafkaTopicConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Component
public class ReactiveOrderEventConsumer {

    // Reactive Sink to stream order events in real-time (WebFlux / Reactor)
    private final Sinks.Many<OrderCreatedEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer();

    @PostConstruct
    public void initReactivePipeline() {
        // Demonstrate Reactive processing pipeline: Non-blocking, Async, Buffering
        eventSink.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .delayElements(Duration.ofMillis(50))
                .flatMap(event -> processEventReactively(event))
                .subscribe(
                        processedEvent -> log.info("[WEBFLUX REACTIVE PIPELINE] Async reactive processing finished for Order: {}",
                                processedEvent.getOrderCode()),
                        error -> log.error("[WEBFLUX REACTIVE PIPELINE ERROR] Reactive stream error: {}", error.getMessage())
                );
    }

    @KafkaListener(topics = KafkaTopicConfig.ORDER_CREATED_TOPIC, groupId = "reactive-webflux-order-group")
    public void consumeAndEmitToReactiveStream(OrderCreatedEvent event) {
        log.info("[WEBFLUX REACTIVE CONSUMER] Ingesting event into Project Reactor Sink for Order: {}", event.getOrderCode());
        eventSink.tryEmitNext(event);
    }

    private Mono<OrderCreatedEvent> processEventReactively(OrderCreatedEvent event) {
        return Mono.fromCallable(() -> {
            log.info("[WEBFLUX REACTIVE ASYNC] Non-blocking reactive telemetry computed for OrderId: {}, Total: {}",
                    event.getOrderId(), event.getTotalAmount());
            return event;
        });
    }

    public Flux<OrderCreatedEvent> getEventStream() {
        return eventSink.asFlux();
    }
}
