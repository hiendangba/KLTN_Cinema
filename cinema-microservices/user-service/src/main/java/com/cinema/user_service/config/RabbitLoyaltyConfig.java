package com.cinema.user_service.config;

import com.cinema.messaging.LoyaltyPointsQueueNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitLoyaltyConfig {

    @Bean
    public Queue loyaltyPointsQueue() {
        return QueueBuilder.durable(LoyaltyPointsQueueNames.QUEUE)
                .deadLetterExchange(LoyaltyPointsQueueNames.DLQ_EXCHANGE)
                .deadLetterRoutingKey(LoyaltyPointsQueueNames.DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange loyaltyPointsExchange() {
        return new DirectExchange(LoyaltyPointsQueueNames.EXCHANGE, true, false);
    }

    @Bean
    public Binding loyaltyPointsBinding(Queue loyaltyPointsQueue, DirectExchange loyaltyPointsExchange) {
        return BindingBuilder.bind(loyaltyPointsQueue).to(loyaltyPointsExchange).with(LoyaltyPointsQueueNames.ROUTING_KEY);
    }

    @Bean
    public Queue loyaltyPointsDlqQueue() {
        return QueueBuilder.durable(LoyaltyPointsQueueNames.DLQ_QUEUE).build();
    }

    @Bean
    public DirectExchange loyaltyPointsDlqExchange() {
        return new DirectExchange(LoyaltyPointsQueueNames.DLQ_EXCHANGE, true, false);
    }

    @Bean
    public Binding loyaltyPointsDlqBinding(Queue loyaltyPointsDlqQueue, DirectExchange loyaltyPointsDlqExchange) {
        return BindingBuilder.bind(loyaltyPointsDlqQueue)
                .to(loyaltyPointsDlqExchange)
                .with(LoyaltyPointsQueueNames.DLQ_ROUTING_KEY);
    }
}
