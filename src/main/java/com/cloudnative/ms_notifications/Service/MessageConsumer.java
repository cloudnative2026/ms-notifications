package com.cloudnative.ms_notifications.Service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cloudnative.ms_notifications.DTO.Message;

@Service
public class MessageConsumer {

    @Autowired
    private NotificationStreams streams;

    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void receive(Message m) {
        System.out.println("Receiving message...");

        try {
            // fake 6 second lag
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println(
                "[Received] User: " + m.getUser() + "\nEndpoint: " +
                        m.getEndpoint() + "\nMessage: " + m.getContent());
        streams.send(m.getUser(), m);
    }

}
