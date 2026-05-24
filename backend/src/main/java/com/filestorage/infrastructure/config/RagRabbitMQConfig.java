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

@Configuration
public class RagRabbitMQConfig {

    public static final String RAG_EXCHANGE = "file-storage.rag.exchange";

    public static final String QUEUE_RAG_FILE_INGEST = "queue.rag.file.ingest";

    public static final String RK_RAG_FILE_INGEST = "rag.file.ingest";

    public static final String RAG_DLX = "file-storage.rag.dlx";
    public static final String RAG_DLQ = "file-storage.rag.dlq";
    public static final String RAG_DL_RK = "rag.dead";

    public static final String LISTENER_FACTORY = "ragListenerContainerFactory";

    @Bean
    public TopicExchange ragExchange() {
        return new TopicExchange(RAG_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange ragDeadLetterExchange() {
        return new DirectExchange(RAG_DLX, true, false);
    }

    @Bean
    public Queue ragFileIngestQueue() {
        return QueueBuilder.durable(QUEUE_RAG_FILE_INGEST)
                .withArgument("x-dead-letter-exchange", RAG_DLX)
                .withArgument("x-dead-letter-routing-key", RAG_DL_RK)
                .build();
    }

    @Bean
    public Queue ragDeadLetterQueue() {
        return QueueBuilder.durable(RAG_DLQ).build();
    }

    @Bean
    public Binding ragFileIngestBinding(Queue ragFileIngestQueue, TopicExchange ragExchange) {
        return BindingBuilder.bind(ragFileIngestQueue).to(ragExchange).with(RK_RAG_FILE_INGEST);
    }

    @Bean
    public Binding ragDlqBinding(Queue ragDeadLetterQueue, DirectExchange ragDeadLetterExchange) {
        return BindingBuilder.bind(ragDeadLetterQueue).to(ragDeadLetterExchange).with(RAG_DL_RK);
    }

    @Bean
    public MethodInterceptor ragRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(5_000, 3.0, 45_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean(name = LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory ragListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new JacksonJsonMessageConverter());
        factory.setAdviceChain(ragRetryInterceptor());
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        return factory;
    }
}
