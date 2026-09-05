package com.digitalbank.ledgerservice.configuration;

import com.digitalbank.ledgerservice.application.port.out.LedgerOutboxDeliveryRepository;
import com.digitalbank.ledgerservice.application.port.out.OutboxEventDeliveryTransport;
import com.digitalbank.ledgerservice.application.service.OutboxDeliveryService;
import com.digitalbank.ledgerservice.application.service.OutboxDeliverySettings;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(LedgerOutboxDeliveryProperties.class)
class LedgerOutboxDeliveryConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ledger.outbox.delivery", name = "enabled", havingValue = "true")
    OutboxDeliveryService outboxDeliveryService(
            LedgerOutboxDeliveryRepository repository,
            OutboxEventDeliveryTransport transport,
            Clock clock,
            LedgerOutboxDeliveryProperties properties) {
        return new OutboxDeliveryService(
                repository,
                transport,
                clock,
                new OutboxDeliverySettings(
                        properties.getBatchSize(),
                        properties.getMaxAttempts(),
                        properties.getLeaseDuration(),
                        properties.getRetryDelay()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "ledger.outbox.delivery", name = "enabled", havingValue = "true")
    OutboxDeliveryScheduler outboxDeliveryScheduler(
            OutboxDeliveryService service, LedgerOutboxDeliveryProperties properties) {
        return new OutboxDeliveryScheduler(service, properties.getPollDelay().toMillis());
    }

    static final class OutboxDeliveryScheduler {

        private final OutboxDeliveryService service;
        private final long pollDelayMillis;

        OutboxDeliveryScheduler(OutboxDeliveryService service, long pollDelayMillis) {
            this.service = service;
            this.pollDelayMillis = pollDelayMillis;
        }

        @Scheduled(fixedDelayString = "${ledger.outbox.delivery.poll-delay:5s}")
        public void deliverPendingEvents() {
            service.deliverPendingEvents();
        }

        public long getPollDelayMillis() {
            return pollDelayMillis;
        }
    }
}
