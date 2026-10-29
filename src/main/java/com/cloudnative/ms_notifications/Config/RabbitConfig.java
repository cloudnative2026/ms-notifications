package com.cloudnative.ms_notifications.Config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
     * Cola durable: sobrevive al reinicio del broker.
     * Los mensajes rechazados (tras agotar reintentos) se reenvian al DLX.
     */
    @Bean
    public Queue accesosQueue() {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(exchangeName + ".dlx")
                .deadLetterRoutingKey(routingKey)
                .build();
    }

    /**
     * Exchange de dead letters: recibe los mensajes que el consumidor no pudo procesar.
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(exchangeName + ".dlx");
    }

    /**
     * DLQ: guarda los mensajes fallidos para revisarlos o reprocesarlos a mano.
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(queueName + ".dlq").build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(routingKey);
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
