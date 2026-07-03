package com.cinema.messaging;

public final class LoyaltyPointsQueueNames {

    public static final String EXCHANGE = "loyalty.points.exchange";
    public static final String ROUTING_KEY = "loyalty.points.sync";
    public static final String QUEUE = "loyalty.points.sync.queue";
    public static final String DLQ_EXCHANGE = "loyalty.points.dlq.exchange";
    public static final String DLQ_ROUTING_KEY = "loyalty.points.dlq";
    public static final String DLQ_QUEUE = "loyalty.points.dlq.queue";

    private LoyaltyPointsQueueNames() {
    }
}
