package com.filestorage.infrastructure.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for audit log events.
 * <p>
 * Producer side: {@code AuditEventRabbitPublisher} converts each {@code AuditEvent}
 * to JSON and publishes to {@link #AUDIT_EXCHANGE} with routing key
 * {@link #RK_AUDIT_EVENT}. Consumer side: {@code AuditEventConsumer} listens on
 * {@link #AUDIT_QUEUE} and persists rows to {@code audit_logs}.
 * <p>
 * Audit events are security-critical — we use {@code durable} queues and a DLQ so
 * messages survive broker restarts and exhausted retries don't disappear silently.
 */
@Configuration
public class AuditRabbitMQConfig {

    public static final String AUDIT_EXCHANGE = "audit.exchange";
    public static final String AUDIT_QUEUE    = "audit.queue";
    public static final String RK_AUDIT_EVENT = "audit.event";

    public static final String AUDIT_DLX   = "audit.dlx";
    public static final String AUDIT_DLQ   = "audit.dlq";
    public static final String AUDIT_DL_RK = "audit.dead";

    public static final String LISTENER_FACTORY = "auditListenerContainerFactory";

    // ── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(AUDIT_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange auditDeadLetterExchange() {
        return new DirectExchange(AUDIT_DLX, true, false);
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", AUDIT_DLX)
                .withArgument("x-dead-letter-routing-key", AUDIT_DL_RK)
                .build();
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(AUDIT_DLQ).build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding auditBinding(Queue auditQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(RK_AUDIT_EVENT);
    }

    @Bean
    public Binding auditDlqBinding(Queue auditDeadLetterQueue, DirectExchange auditDeadLetterExchange) {
        return BindingBuilder.bind(auditDeadLetterQueue).to(auditDeadLetterExchange).with(AUDIT_DL_RK);
    }

    // ── Listener factory ─────────────────────────────────────────────────────

    /**
     * Retry interceptor: 3 attempts with 2 s / 6 s / 18 s back-off. Audit writes
     * are simple inserts — typical failure is transient DB hiccup that recovers
     * fast. After exhaustion the message is dead-lettered to {@link #AUDIT_DLQ}.
     */
    @Bean
    public MethodInterceptor auditRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(2_000, 3.0, 18_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean(name = LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory auditListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new JacksonJsonMessageConverter());
        factory.setAdviceChain(auditRetryInterceptor());
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(4);
        return factory;
    }
}
