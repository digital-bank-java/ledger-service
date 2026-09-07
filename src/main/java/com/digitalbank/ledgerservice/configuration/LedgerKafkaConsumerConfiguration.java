package com.digitalbank.ledgerservice.configuration;

import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class LedgerKafkaConsumerConfiguration {

    private static final long RECOVERY_RETRY_INTERVAL_MILLIS = 1_000L;
    private static final long RECOVERY_RETRY_ATTEMPTS = 2L;

    @Bean(name = "ledgerKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> ledgerKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaOperations<String, String> kafkaOperations,
            @org.springframework.beans.factory.annotation.Value("${ledger.posting.consumer.topic:ledger.posting.requested.v1}")
                    String ledgerPostingTopic) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);

        var errorHandler = new DefaultErrorHandler(
                deadLetterPublishingRecoverer(kafkaOperations, ledgerPostingTopic),
                new FixedBackOff(RECOVERY_RETRY_INTERVAL_MILLIS, RECOVERY_RETRY_ATTEMPTS));
        errorHandler.setAckAfterHandle(true);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaOperations<String, String> kafkaOperations, String ledgerPostingTopic) {
        BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver =
                (record, exception) -> new TopicPartition(ledgerPostingTopic + ".dlq", record.partition());
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations, destinationResolver);
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }
}
