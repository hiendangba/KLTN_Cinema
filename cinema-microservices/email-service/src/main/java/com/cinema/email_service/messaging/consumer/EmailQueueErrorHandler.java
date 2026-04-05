package com.cinema.email_service.messaging.consumer;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.messaging.MessageHeaders;
import org.springframework.stereotype.Component;

@Component("emailListenerErrorHandler")
@Slf4j
public class EmailQueueErrorHandler implements RabbitListenerErrorHandler {

    @Override
    public Object handleError(Message amqpMessage,
                              Channel channel,
                              org.springframework.messaging.Message<?> message,
                              ListenerExecutionFailedException exception) {
        assert message != null;
        MessageHeaders headers = message.getHeaders();
        log.error("Email listener failed to process message: headers={}, payload={}",
                headers, message.getPayload(), exception);
        return null;
    }
}
