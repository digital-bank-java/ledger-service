package com.digitalbank.ledgerservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.ledgerservice.application.port.out.LedgerEntryRepository;
import com.digitalbank.ledgerservice.application.service.LedgerService;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import com.digitalbank.ledgerservice.domain.model.LedgerLineType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class LedgerPersistenceIT {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-03T10:15:30Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void postsAndLoadsLedgerEntry() {
        var debitAccountId = UUID.randomUUID();
        var creditAccountId = UUID.randomUUID();

        var posted = ledgerService.postLedgerEntry(new com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand(
                "ledger-posting-001",
                "Settlement posting",
                "AED",
                Instant.parse("2026-07-03T09:00:00Z"),
                List.of(new com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand.Line(
                        debitAccountId, new BigDecimal("125.50"), LedgerLineType.DEBIT)),
                List.of(new com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand.Line(
                        creditAccountId, new BigDecimal("125.50"), LedgerLineType.CREDIT))));

        var saved = ledgerEntryRepository.findById(new LedgerEntryId(UUID.fromString(posted.ledgerEntryId())));

        assertThat(saved).hasValueSatisfying(entry -> {
            assertThat(entry.postingRequestId()).isEqualTo("ledger-posting-001");
            assertThat(entry.description()).isEqualTo("Settlement posting");
            assertThat(entry.currency()).isEqualTo("AED");
            assertThat(entry.effectiveAt()).isEqualTo(Instant.parse("2026-07-03T09:00:00Z"));
            assertThat(entry.createdAt()).isEqualTo(FIXED_NOW);
            assertThat(entry.totalDebitAmount()).isEqualByComparingTo("125.50");
            assertThat(entry.totalCreditAmount()).isEqualByComparingTo("125.50");
            assertThat(entry.lines()).hasSize(2);
        });
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
