package com.digitalbank.ledgerservice.adapter.out.events;

import com.digitalbank.ledgerservice.application.port.out.ClaimedOutboxEvent;
import com.digitalbank.ledgerservice.application.port.out.OutboxEventDeliveryTransport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "ledger.outbox.delivery", name = "enabled", havingValue = "true")
class KafkaOutboxEventDeliveryTransport implements OutboxEventDeliveryTransport {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaOutboxEventRecordFactory recordFactory = new KafkaOutboxEventRecordFactory();

    KafkaOutboxEventDeliveryTransport(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void deliver(ClaimedOutboxEvent event) {
        kafkaTemplate.send(recordFactory.create(event)).join();
    }
}
