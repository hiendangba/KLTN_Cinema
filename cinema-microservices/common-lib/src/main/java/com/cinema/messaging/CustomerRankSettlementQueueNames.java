package com.cinema.messaging;

public final class CustomerRankSettlementQueueNames {
    public static final String EXCHANGE = "customer.rank.settlement.exchange";
    public static final String ROUTING_KEY = "customer.rank.settlement.sync";
    public static final String QUEUE = "customer.rank.settlement.sync.queue";
    public static final String DLQ_EXCHANGE = "customer.rank.settlement.dlq.exchange";
    public static final String DLQ_ROUTING_KEY = "customer.rank.settlement.dlq";
    public static final String DLQ_QUEUE = "customer.rank.settlement.dlq.queue";

    private CustomerRankSettlementQueueNames() {
    }
}
