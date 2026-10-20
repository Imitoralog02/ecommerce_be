package com.neora.order.config;
import org.apache.kafka.clients.admin.NewTopic; import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*; import org.springframework.kafka.config.TopicBuilder;
@Configuration public class KafkaProducerConfig {
    @Bean NewTopic orderTopic(@Value("${app.kafka.order-topic:order.lifecycle.v1}")String topic){return TopicBuilder.name(topic).partitions(3).replicas(1).build();}
}
