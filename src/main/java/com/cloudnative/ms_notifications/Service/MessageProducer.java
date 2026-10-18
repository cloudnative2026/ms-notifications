package com.cloudnative.ms_notifications.Service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cloudnative.ms_notifications.DTO.Message;

@Service
public class MessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    public void send(Message m) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, m);
        System.out.println("Message sent to : " + m.getUser());
    }

}
