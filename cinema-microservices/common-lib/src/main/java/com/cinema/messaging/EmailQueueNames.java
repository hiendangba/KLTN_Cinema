package com.cinema.messaging;

public final class EmailQueueNames {
    public static final String EXCHANGE = "email.exchange";
    public static final String ROUTING_KEY = "email.send";
    public static final String QUEUE = "email.send.queue";

    private EmailQueueNames() {
    }
}
