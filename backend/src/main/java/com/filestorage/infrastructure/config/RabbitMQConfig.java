package com.filestorage.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for email events.
 * <p>
 * Exchange: {@code email.exchange} (topic)<br>
 * Queue:    {@code email.queue}<br>
 * Routing keys: {@code email.reset-password}, {@code email.verify-email}, {@code email.initial-password}
 */
@Configuration
public class RabbitMQConfig {

    public static final String EMAIL_EXCHANGE            = "email.exchange";
    public static final String EMAIL_QUEUE               = "email.queue";
    public static final String EMAIL_ROUTING_KEY_RESET   = "email.reset-password";
    public static final String EMAIL_ROUTING_KEY_VERIFY  = "email.verify-email";
    public static final String EMAIL_ROUTING_KEY_INITIAL = "email.initial-password";

    @Bean
    public TopicExchange emailExchange() {
        return new TopicExchange(EMAIL_EXCHANGE);
    }

    @Bean
    public Queue emailQueue() {
        return new Queue(EMAIL_QUEUE, true);
    }

    @Bean
    public Binding resetPasswordBinding(Queue emailQueue, TopicExchange emailExchange) {
        return BindingBuilder.bind(emailQueue).to(emailExchange).with(EMAIL_ROUTING_KEY_RESET);
    }

    @Bean
    public Binding verifyEmailBinding(Queue emailQueue, TopicExchange emailExchange) {
        return BindingBuilder.bind(emailQueue).to(emailExchange).with(EMAIL_ROUTING_KEY_VERIFY);
    }

    @Bean
    public Binding initialPasswordBinding(Queue emailQueue, TopicExchange emailExchange) {
        return BindingBuilder.bind(emailQueue).to(emailExchange).with(EMAIL_ROUTING_KEY_INITIAL);
    }

    /**
     * Serialize RabbitMQ messages as JSON (consumed by email micro-service).
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
