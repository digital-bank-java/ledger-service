package com.digitalbank.ledgerservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class LedgerOutboxMigrationIT {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void quarantinesLegacyPendingOutboxRowsWhenHardeningTheSchema() throws Exception {
        flyway(7).migrate();
        var eventId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "insert into ledger_outbox_events "
                                + "(event_id, event_type, aggregate_id, posting_request_id, correlation_id, "
                                + "causation_id, payload, status, attempts, created_at) "
                                + "values (?, 'LedgerPostingCompleted.v1', ?, null, 'correlation', 'causation', "
                                + "'{}'::jsonb, 'PENDING', 0, now())")) {
            statement.setObject(1, eventId);
            statement.setString(2, UUID.randomUUID().toString());
            statement.executeUpdate();
        }

        assertThatCode(() -> flyway(8).migrate()).doesNotThrowAnyException();

        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.prepareStatement(
                        "select status, quarantined_at, last_error from ledger_outbox_events where event_id = ?")) {
            statement.setObject(1, eventId);
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("status")).isEqualTo("QUARANTINED");
                assertThat(resultSet.getTimestamp("quarantined_at")).isNotNull();
                assertThat(resultSet.getString("last_error"))
                        .isEqualTo("Legacy outbox event is missing governed posting metadata");
            }
        }
    }

    private static Flyway flyway(int target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(String.valueOf(target))
                .load();
    }
}
