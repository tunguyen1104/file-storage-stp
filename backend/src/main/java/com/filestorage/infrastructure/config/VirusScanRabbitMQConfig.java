package com.filestorage.infrastructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for virus-scan events.
 * <p>
 * Exchange: {@code file.virus-scan.exchange} (direct)<br>
 * Queues:
 * <ul>
 *   <li>{@code file.virus-scan.request} — ConfirmUploadService → VirusScanConsumer</li>
 *   <li>{@code file.virus-scan.result}  — VirusScanConsumer → VirusScanResultHandler</li>
 * </ul>
 * Dead-letter topology: both queues forward rejected messages to {@code file.virus-scan.dlx} / {@code file.virus-scan.dlq}.
 */
@Configuration
public class VirusScanRabbitMQConfig {

    public static final String VIRUS_SCAN_EXCHANGE     = "file.virus-scan.exchange";

    public static final String VIRUS_SCAN_REQUEST_QUEUE = "file.virus-scan.request";
    public static final String VIRUS_SCAN_RESULT_QUEUE  = "file.virus-scan.result";

    public static final String VIRUS_SCAN_REQUEST_RK    = "virus-scan.request";
    public static final String VIRUS_SCAN_RESULT_RK     = "virus-scan.result";

    // Dead-letter topology
    public static final String VIRUS_SCAN_DLX  = "file.virus-scan.dlx";
    public static final String VIRUS_SCAN_DLQ  = "file.virus-scan.dlq";
    public static final String VIRUS_SCAN_DL_RK = "virus-scan.dead";

    @Bean
    public DirectExchange virusScanExchange() {
        return new DirectExchange(VIRUS_SCAN_EXCHANGE);
    }

    @Bean
    public DirectExchange virusScanDeadLetterExchange() {
        return new DirectExchange(VIRUS_SCAN_DLX);
    }

    @Bean
    public Queue virusScanRequestQueue() {
        return QueueBuilder.durable(VIRUS_SCAN_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", VIRUS_SCAN_DLX)
                .withArgument("x-dead-letter-routing-key", VIRUS_SCAN_DL_RK)
                .build();
    }

    @Bean
    public Queue virusScanResultQueue() {
        return QueueBuilder.durable(VIRUS_SCAN_RESULT_QUEUE)
                .withArgument("x-dead-letter-exchange", VIRUS_SCAN_DLX)
                .withArgument("x-dead-letter-routing-key", VIRUS_SCAN_DL_RK)
                .build();
    }

    @Bean
    public Queue virusScanDeadLetterQueue() {
        return QueueBuilder.durable(VIRUS_SCAN_DLQ).build();
    }

    @Bean
    public Binding virusScanRequestBinding(Queue virusScanRequestQueue, DirectExchange virusScanExchange) {
        return BindingBuilder.bind(virusScanRequestQueue).to(virusScanExchange).with(VIRUS_SCAN_REQUEST_RK);
    }

    @Bean
    public Binding virusScanResultBinding(Queue virusScanResultQueue, DirectExchange virusScanExchange) {
        return BindingBuilder.bind(virusScanResultQueue).to(virusScanExchange).with(VIRUS_SCAN_RESULT_RK);
    }

    @Bean
    public Binding virusScanDlqBinding(Queue virusScanDeadLetterQueue, DirectExchange virusScanDeadLetterExchange) {
        return BindingBuilder.bind(virusScanDeadLetterQueue).to(virusScanDeadLetterExchange).with(VIRUS_SCAN_DL_RK);
    }
}
