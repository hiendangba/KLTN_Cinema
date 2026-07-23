package com.cinema.user_service.config;

import com.cinema.messaging.CustomerRankSettlementQueueNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitCustomerRankSettlementConfig {

    @Bean
    public Queue customerRankSettlementQueue() {
        return QueueBuilder.durable(CustomerRankSettlementQueueNames.QUEUE)
                .deadLetterExchange(CustomerRankSettlementQueueNames.DLQ_EXCHANGE)
                .deadLetterRoutingKey(CustomerRankSettlementQueueNames.DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange customerRankSettlementExchange() {
        return new DirectExchange(CustomerRankSettlementQueueNames.EXCHANGE, true, false);
    }

    @Bean
    public Binding customerRankSettlementBinding(
            Queue customerRankSettlementQueue,
            DirectExchange customerRankSettlementExchange) {
        return BindingBuilder.bind(customerRankSettlementQueue)
                .to(customerRankSettlementExchange)
                .with(CustomerRankSettlementQueueNames.ROUTING_KEY);
    }

    @Bean
    public Queue customerRankSettlementDlq() {
        return QueueBuilder.durable(CustomerRankSettlementQueueNames.DLQ_QUEUE).build();
    }

    @Bean
    public DirectExchange customerRankSettlementDlqExchange() {
        return new DirectExchange(CustomerRankSettlementQueueNames.DLQ_EXCHANGE, true, false);
    }

    @Bean
    public Binding customerRankSettlementDlqBinding(
            Queue customerRankSettlementDlq,
            DirectExchange customerRankSettlementDlqExchange) {
        return BindingBuilder.bind(customerRankSettlementDlq)
                .to(customerRankSettlementDlqExchange)
                .with(CustomerRankSettlementQueueNames.DLQ_ROUTING_KEY);
    }
}
