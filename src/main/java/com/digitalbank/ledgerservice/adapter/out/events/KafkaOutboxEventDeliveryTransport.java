package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent;
import com.digitalbank.ledgerservice.application.port.out.OutboxEventDeliveryTransport;
import com.digitalbank.ledgerservice.configuration.LedgerOutboxDeliveryProperties;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledger.outbox.delivery", name = "enabled", havingValue = "true")
class KafkaOutboxEventDeliveryTransport implements OutboxEventDeliveryTransport {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Duration publishTimeout;
    private final KafkaOutboxEventRecordFactory recordFactory = new KafkaOutboxEventRecordFactory();

    KafkaOutboxEventDeliveryTransport(
            KafkaTemplate<String, String> kafkaTemplate, LedgerOutboxDeliveryProperties properties) {
        this(kafkaTemplate, properties.getPublishTimeout());
    }

    KafkaOutboxEventDeliveryTransport(KafkaTemplate<String, String> kafkaTemplate, Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        if (publishTimeout == null || publishTimeout.isNegative() || publishTimeout.isZero()) {
            throw new IllegalArgumentException("Kafka publish timeout must be positive");
        }
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void deliver(ClaimedOutboxEvent event) {
        try {
            kafkaTemplate
                    .send(recordFactory.create(event))
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out publishing ledger outbox event " + event.eventId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing ledger outbox event " + event.eventId(), exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to publish ledger outbox event " + event.eventId(), exception);
        }
    }
}
