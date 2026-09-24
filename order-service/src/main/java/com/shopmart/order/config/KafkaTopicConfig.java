package com.shopmart.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_CREATED_TOPIC = "order-created-topic";
    public static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved-topic";
    public static final String INVENTORY_FAILED_TOPIC = "inventory-failed-topic";
    public static final String PAYMENT_COMPLETED_TOPIC = "payment-completed-topic";
    public static final String PAYMENT_FAILED_TOPIC = "payment-failed-topic";
    public static final String INVENTORY_COMPENSATE_TOPIC = "inventory-compensate-topic";

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED_TOPIC).partitions(1).replicas(1).build();
    }
}
