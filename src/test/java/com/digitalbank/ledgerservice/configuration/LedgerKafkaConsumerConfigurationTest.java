package com.digitalbank.ledgerservice.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

class LedgerKafkaConsumerConfigurationTest {

    private static final String POSTING_TOPIC = "ledger.posting.requested.v1";
    private static final String DLQ_TOPIC = POSTING_TOPIC + ".dlq";

    private final LedgerKafkaConsumerConfiguration configuration = new LedgerKafkaConsumerConfiguration();

    @Test
    void routesRecoveredPostingRecordToSamePartitionOnGovernedDlq() {
        var sentRecord = new AtomicReference<ProducerRecord<?, ?>>();
        var operations = recordingKafkaOperations(sentRecord);
        var recoverer = configuration.deadLetterPublishingRecoverer(operations, POSTING_TOPIC);
        var record = new ConsumerRecord<>(POSTING_TOPIC, 2, 17L, "transfer-1", "malformed-json");

        recoverer.accept(record, null, new IllegalArgumentException("malformed payload"));

        assertThat(sentRecord.get().topic()).isEqualTo(DLQ_TOPIC);
        assertThat(sentRecord.get().partition()).isEqualTo(2);
        assertThat(sentRecord.get().key()).isEqualTo("transfer-1");
        assertThat(sentRecord.get().value()).isEqualTo("malformed-json");
    }

    @Test
    void configuresBoundedDefaultErrorHandlerOnLedgerListenerFactory() {
        ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                configuration.ledgerKafkaListenerContainerFactory(consumerFactory, recordingKafkaOperations(new AtomicReference<>()), POSTING_TOPIC);

        assertThat(factory.getConsumerFactory()).isSameAs(consumerFactory);
        assertThat(factory.createContainer(POSTING_TOPIC).getCommonErrorHandler()).isInstanceOf(DefaultErrorHandler.class);
        assertThat(factory.getContainerProperties().getAckMode())
                .isEqualTo(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
    }

    @SuppressWarnings("unchecked")
    private static KafkaOperations<String, String> recordingKafkaOperations(AtomicReference<ProducerRecord<?, ?>> sentRecord) {
        return (KafkaOperations<String, String>) Proxy.newProxyInstance(
                KafkaOperations.class.getClassLoader(),
                new Class<?>[] {KafkaOperations.class},
                (proxy, method, arguments) -> {
                    if ("send".equals(method.getName()) && arguments != null) {
                        for (Object argument : arguments) {
                            if (argument instanceof ProducerRecord<?, ?> producerRecord) {
                                sentRecord.set(producerRecord);
                                return CompletableFuture.completedFuture(null);
                            }
                        }
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class || returnType == short.class || returnType == int.class || returnType == long.class) {
            return 0;
        }
        if (returnType == float.class || returnType == double.class) {
            return 0.0;
        }
        return null;
    }
}
