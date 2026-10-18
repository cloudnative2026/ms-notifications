package com.cloudnative.ms_notifications.Config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue}")
    private String queueName;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    /**
     * Exchange tipo topic: enruta segun patrones de routing key.
     * El productor publica AQUI, nunca directo a una cola.
     */
    @Bean
    public TopicExchange accesosExchange() {
        return new TopicExchange(exchangeName);
    }

    /**
     * Cola durable (true): sobrevive al reinicio del broker.
     * Con false, los mensajes se perderian al reiniciar RabbitMQ.
     */
    @Bean
    public Queue accesosQueue() {
        return new Queue(queueName, true);
    }

    /**
     * Binding: la regla que une exchange y cola.
     * Sin esto el mensaje llega al exchange y se descarta en silencio, sin ningun
     * error.
     */
    @Bean
    public Binding accesosBinding(Queue accesosQueue, TopicExchange accesosExchange) {
        return BindingBuilder
                .bind(accesosQueue)
                .to(accesosExchange)
                .with(routingKey);
    }

    /**
     * Serializa a JSON en vez de binario Java.
     * Sin esto el mensaje es ilegible en la consola de RabbitMQ
     * y queda acoplado a las clases Java de este proyecto.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
