package com.filestorage.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.aopalliance.intercept.MethodInterceptor;
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

    // Dead-Letter topology
    public static final String EMAIL_DLX   = "email.dlx";
    public static final String EMAIL_DLQ   = "email.dlq";
    public static final String EMAIL_DL_RK = "email.dead";

    @Bean
    public TopicExchange emailExchange() {
        return new TopicExchange(EMAIL_EXCHANGE);
    }

    /** Dead-letter exchange — receives rejected messages from email.queue */
    @Bean
    public DirectExchange emailDeadLetterExchange() {
        return new DirectExchange(EMAIL_DLX);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", EMAIL_DLX)
                .withArgument("x-dead-letter-routing-key", EMAIL_DL_RK)
                .build();
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(EMAIL_DLQ).build();
    }

    @Bean
    public Binding dlqBinding(Queue emailDeadLetterQueue, DirectExchange emailDeadLetterExchange) {
        return BindingBuilder.bind(emailDeadLetterQueue).to(emailDeadLetterExchange).with(EMAIL_DL_RK);
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

    /**
     * Retry interceptor: up to 3 attempts with 2 s / 5 s / 10 s back-off.
     * After exhaustion, {@link RejectAndDontRequeueRecoverer} rejects the message
     * so RabbitMQ routes it to the DLQ via the dead-letter-exchange.
     */
    @Bean
    public MethodInterceptor emailRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(2_000, 2.5, 10_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    /**
     * Listener container factory that applies the retry interceptor and uses the
     * JSON message converter.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAdviceChain(emailRetryInterceptor());
        return factory;
    }
}
