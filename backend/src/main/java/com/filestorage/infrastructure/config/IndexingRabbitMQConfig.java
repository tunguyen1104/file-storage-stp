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
 * RabbitMQ topology for the indexing pipeline (files + folders).
 *
 * <p>Note on naming: the rest of this codebase uses {@code <domain>.<event>}
 * (e.g. {@code email.queue}, {@code file.virus-scan.request}). The queue names
 * here ({@code queue.file.uploaded}, …) invert that convention because they
 * were specified verbatim in the indexing spec. We keep the spec's names so
 * external consumers don't have to be updated.
 *
 * <p>Exchange: {@code file-storage.indexing.exchange} (topic, durable)<br>
 * Queues: {@code queue.file.{uploaded,updated,deleted}},
 * {@code queue.folder.{upserted,deleted}}<br>
 * DLX/DLQ: {@code file-storage.indexing.dlx} / {@code file-storage.indexing.dlq}
 */
@Configuration
public class IndexingRabbitMQConfig {

    public static final String INDEXING_EXCHANGE = "file-storage.indexing.exchange";

    public static final String QUEUE_FILE_UPLOADED   = "queue.file.uploaded";
    public static final String QUEUE_FILE_UPDATED    = "queue.file.updated";
    public static final String QUEUE_FILE_DELETED    = "queue.file.deleted";
    public static final String QUEUE_FOLDER_UPSERTED = "queue.folder.upserted";
    public static final String QUEUE_FOLDER_DELETED  = "queue.folder.deleted";

    public static final String RK_FILE_UPLOADED   = "file.uploaded";
    public static final String RK_FILE_UPDATED    = "file.updated";
    public static final String RK_FILE_DELETED    = "file.deleted";
    public static final String RK_FOLDER_UPSERTED = "folder.upserted";
    public static final String RK_FOLDER_DELETED  = "folder.deleted";

    public static final String INDEXING_DLX   = "file-storage.indexing.dlx";
    public static final String INDEXING_DLQ   = "file-storage.indexing.dlq";
    public static final String INDEXING_DL_RK = "indexing.dead";

    public static final String LISTENER_FACTORY = "indexingListenerContainerFactory";

    // ── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange indexingExchange() {
        return new TopicExchange(INDEXING_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange indexingDeadLetterExchange() {
        return new DirectExchange(INDEXING_DLX, true, false);
    }

    // ── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue indexingFileUploadedQueue() {
        return durableWithDlx(QUEUE_FILE_UPLOADED);
    }

    @Bean
    public Queue indexingFileUpdatedQueue() {
        return durableWithDlx(QUEUE_FILE_UPDATED);
    }

    @Bean
    public Queue indexingFileDeletedQueue() {
        return durableWithDlx(QUEUE_FILE_DELETED);
    }

    @Bean
    public Queue indexingFolderUpsertedQueue() {
        return durableWithDlx(QUEUE_FOLDER_UPSERTED);
    }

    @Bean
    public Queue indexingFolderDeletedQueue() {
        return durableWithDlx(QUEUE_FOLDER_DELETED);
    }

    @Bean
    public Queue indexingDeadLetterQueue() {
        return QueueBuilder.durable(INDEXING_DLQ).build();
    }

    // ── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding fileUploadedBinding(Queue indexingFileUploadedQueue, TopicExchange indexingExchange) {
        return BindingBuilder.bind(indexingFileUploadedQueue).to(indexingExchange).with(RK_FILE_UPLOADED);
    }

    @Bean
    public Binding fileUpdatedBinding(Queue indexingFileUpdatedQueue, TopicExchange indexingExchange) {
        return BindingBuilder.bind(indexingFileUpdatedQueue).to(indexingExchange).with(RK_FILE_UPDATED);
    }

    @Bean
    public Binding fileDeletedBinding(Queue indexingFileDeletedQueue, TopicExchange indexingExchange) {
        return BindingBuilder.bind(indexingFileDeletedQueue).to(indexingExchange).with(RK_FILE_DELETED);
    }

    @Bean
    public Binding folderUpsertedBinding(Queue indexingFolderUpsertedQueue, TopicExchange indexingExchange) {
        return BindingBuilder.bind(indexingFolderUpsertedQueue).to(indexingExchange).with(RK_FOLDER_UPSERTED);
    }

    @Bean
    public Binding folderDeletedBinding(Queue indexingFolderDeletedQueue, TopicExchange indexingExchange) {
        return BindingBuilder.bind(indexingFolderDeletedQueue).to(indexingExchange).with(RK_FOLDER_DELETED);
    }

    @Bean
    public Binding indexingDlqBinding(Queue indexingDeadLetterQueue, DirectExchange indexingDeadLetterExchange) {
        return BindingBuilder.bind(indexingDeadLetterQueue).to(indexingDeadLetterExchange).with(INDEXING_DL_RK);
    }

    // ── Listener factory ─────────────────────────────────────────────────────

    /**
     * Retry interceptor: 3 attempts with 5 s / 15 s / 45 s back-off. Text extraction
     * for large PDFs can take seconds, and the embedded ES client may transiently
     * fail under load — these intervals give it room to recover before the
     * {@link RejectAndDontRequeueRecoverer} routes the message to the DLQ.
     */
    @Bean
    public MethodInterceptor indexingRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(5_000, 3.0, 45_000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean(name = LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory indexingListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new JacksonJsonMessageConverter());
        factory.setAdviceChain(indexingRetryInterceptor());
        factory.setDefaultRequeueRejected(false);
        // Extraction is CPU-bound; pull one message at a time per consumer.
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(2);
        return factory;
    }

    private static Queue durableWithDlx(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", INDEXING_DLX)
                .withArgument("x-dead-letter-routing-key", INDEXING_DL_RK)
                .build();
    }
}
