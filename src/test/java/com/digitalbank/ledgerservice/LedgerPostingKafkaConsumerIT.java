package com.digitalbank.ledgerservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "ledger.posting.consumer.enabled=false")
@Testcontainers
class LedgerPostingKafkaConsumerIT {

    private static final String CONSUMER_CLASS_NAME =
            "com.digitalbank.ledgerservice.adapter.in.kafka.LedgerPostingRequestedConsumer";
    private static final Instant FIXED_NOW = Instant.parse("2026-09-01T08:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consumesGovernedPostingCommandAndPersistsInboxAndCompletionOutbox() throws Exception {
        var eventId = UUID.randomUUID();
        var transactionId = "transfer-kafka-001";
        var record = postingRecord(
                eventId,
                transactionId,
                "posting-kafka-001",
                "correlation-kafka-001",
                "causation-kafka-001");

        var consumer = findConsumerBean();

        assertThat(consumer).as("ledger posting Kafka consumer bean").isNotNull();
        invokeConsume(consumer, record);

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_entries where posting_request_id = ?",
                        Integer.class,
                        "posting-kafka-001"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_outbox_events where posting_request_id = ? "
                                + "and event_type = 'LedgerPostingCompleted.v1'",
                        Integer.class,
                        "posting-kafka-001"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_posting_command_inbox where event_id = ?",
                        Integer.class,
                        eventId))
                .isEqualTo(1);
    }

    @Test
    void duplicateRedeliveryDoesNotCreateSecondLedgerEntryOrSecondCompletionEvent() throws Exception {
        var eventId = UUID.randomUUID();
        var record = postingRecord(
                eventId,
                "transfer-kafka-replay-001",
                "posting-kafka-replay-001",
                "correlation-kafka-replay-001",
                "causation-kafka-replay-001");
        var consumer = findConsumerBean();

        invokeConsume(consumer, record);
        invokeConsume(consumer, record);

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_entries where posting_request_id = ?",
                        Integer.class,
                        "posting-kafka-replay-001"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_outbox_events where posting_request_id = ? "
                                + "and event_type = 'LedgerPostingCompleted.v1'",
                        Integer.class,
                        "posting-kafka-replay-001"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_posting_command_inbox where event_id = ?",
                        Integer.class,
                        eventId))
                .isEqualTo(1);
    }

    @Test
    void headerPayloadMismatchRecordsTerminalFailureWithoutPostingLedgerEntry() throws Exception {
        var eventId = UUID.randomUUID();
        var record = postingRecord(
                eventId,
                "transfer-kafka-failure-001",
                "posting-kafka-failure-001",
                "correlation-kafka-failure-payload",
                "causation-kafka-failure-001");
        record.headers().remove("correlation-id");
        record.headers().add("correlation-id", "correlation-kafka-failure-header".getBytes());

        invokeConsume(findConsumerBean(), record);

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_entries where posting_request_id = ?",
                        Integer.class,
                        "posting-kafka-failure-001"))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_posting_failure_decisions where posting_request_id = ?",
                        Integer.class,
                        "posting-kafka-failure-001"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_outbox_events where posting_request_id = ? "
                                + "and event_type = 'LedgerPostingFailed.v1'",
                        Integer.class,
                        "posting-kafka-failure-001"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from ledger_posting_command_inbox where event_id = ?",
                        Integer.class,
                        eventId))
                .isEqualTo(1);
    }

    @Test
    void persistedInboxRowsAreAppendOnly() throws Exception {
        var eventId = UUID.randomUUID();
        invokeConsume(
                findConsumerBean(),
                postingRecord(
                        eventId,
                        "transfer-kafka-append-only-001",
                        "posting-kafka-append-only-001",
                        "correlation-kafka-append-only-001",
                        "causation-kafka-append-only-001"));

        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "update ledger_posting_command_inbox set posting_request_id = ? where event_id = ?",
                                "tampered-posting-request",
                                eventId),
                        DataAccessException.class))
                .isNotNull();
        assertThat(catchThrowableOfType(
                        () -> jdbcTemplate.update(
                                "delete from ledger_posting_command_inbox where event_id = ?", eventId),
                        DataAccessException.class))
                .isNotNull();
    }

    private Object findConsumerBean() {
        return applicationContext.getBeansOfType(Object.class).values().stream()
                .filter(bean -> CONSUMER_CLASS_NAME.equals(AopUtils.getTargetClass(bean).getName()))
                .findFirst()
                .orElse(null);
    }

    private static void invokeConsume(Object consumer, ConsumerRecord<String, String> record) throws Exception {
        Method method = AopUtils.getTargetClass(consumer).getDeclaredMethod("consume", ConsumerRecord.class);
        method.setAccessible(true);
        ReflectionTestUtils.invokeMethod(consumer, method.getName(), record);
    }

    private ConsumerRecord<String, String> postingRecord(
            UUID eventId,
            String transactionId,
            String postingRequestId,
            String correlationId,
            String causationId)
            throws Exception {
        var record = new ConsumerRecord<>(
                "ledger.posting.requested.v1",
                0,
                0L,
                transactionId,
                objectMapper.writeValueAsString(Map.ofEntries(
                        Map.entry("eventId", eventId.toString()),
                        Map.entry("eventType", "LedgerPostingRequested.v1"),
                        Map.entry("schemaVersion", "1.0.0"),
                        Map.entry("producer", "transaction-service"),
                        Map.entry("occurredAt", "2026-09-01T07:55:00Z"),
                        Map.entry("aggregateId", transactionId),
                        Map.entry("correlationId", correlationId),
                        Map.entry("causationId", causationId),
                        Map.entry("transactionId", transactionId),
                        Map.entry("reservationRequestId", "reservation-" + postingRequestId),
                        Map.entry("postingRequestId", postingRequestId),
                        Map.entry("description", "Kafka posting"),
                        Map.entry("currency", "AED"),
                        Map.entry("effectiveAt", "2026-09-01T07:50:00Z"),
                        Map.entry("debitLines", new Object[] {Map.of(
                                "accountId", UUID.randomUUID().toString(),
                                "amount", new BigDecimal("25.00"))}),
                        Map.entry("creditLines", new Object[] {Map.of(
                                "accountId", UUID.randomUUID().toString(),
                                "amount", new BigDecimal("25.00"))}))));
        headers(eventId, correlationId, causationId).forEach(record.headers()::add);
        return record;
    }

    private static RecordHeaders headers(UUID eventId, String correlationId, String causationId) {
        var headers = new RecordHeaders();
        headers.add("event-id", eventId.toString().getBytes());
        headers.add("correlation-id", correlationId.getBytes());
        headers.add("causation-id", causationId.getBytes());
        headers.add("producer", "transaction-service".getBytes());
        headers.add("schema-version", "1.0.0".getBytes());
        headers.add("occurred-at", "2026-09-01T07:55:00Z".getBytes());
        return headers;
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
