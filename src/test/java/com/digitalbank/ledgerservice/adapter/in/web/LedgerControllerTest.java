package com.digitalbank.ledgerservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.ledgerservice.application.port.in.GetLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.in.LedgerEntryView;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerReversalCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerReversalInputPort;
import com.digitalbank.ledgerservice.application.port.in.PostingResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LedgerControllerTest {

    private final RecordingPostLedgerEntryInputPort postingPort = new RecordingPostLedgerEntryInputPort();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LedgerController(
                    postingPort, new NoOpGetLedgerEntryInputPort(), new NoOpPostLedgerReversalInputPort()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void mapsEventMetadataIntoPostingCommand() throws Exception {
        mockMvc.perform(post("/internal/v1/ledger-entries")
                        .header("X-Correlation-Id", "correlation-controller-test")
                        .header("X-Causation-Id", "causation-controller-test")
                        .contentType("application/json")
                        .content(validPostingJson()))
                .andExpect(status().isCreated());

        assertThat(postingPort.command.toString())
                .contains(
                        "correlation-controller-test",
                        "causation-controller-test",
                        "transaction-controller-test",
                        "reservation-controller-test");
    }

    @Test
    void rejectsBlankCorrelationHeader() throws Exception {
        mockMvc.perform(post("/internal/v1/ledger-entries")
                        .header("X-Correlation-Id", " ")
                        .header("X-Causation-Id", "causation-controller-test")
                        .contentType("application/json")
                        .content(validPostingJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankCausationHeaderOnReversal() throws Exception {
        mockMvc.perform(post("/internal/v1/ledger-entries/" + UUID.randomUUID() + "/reversals")
                        .header("X-Correlation-Id", "correlation-controller-test")
                        .header("X-Causation-Id", " ")
                        .contentType("application/json")
                        .content("""
                                {
                                  "postingRequestId": "controller-reversal-001",
                                  "description": "Controller reversal",
                                  "effectiveAt": "2026-07-03T09:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAmountsWithMoreThanFourDecimalPlaces() throws Exception {
        mockMvc.perform(post("/internal/v1/ledger-entries")
                        .header("X-Correlation-Id", "correlation-controller-test")
                        .header("X-Causation-Id", "causation-controller-test")
                        .contentType("application/json")
                        .content(validPostingJson().replace("100.00", "100.00001")))
                .andExpect(status().isBadRequest());
    }

    private static String validPostingJson() {
        return """
                {
                  "postingRequestId": "controller-posting-001",
                  "description": "Controller posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "transactionId": "transaction-controller-test",
                  "reservationRequestId": "reservation-controller-test",
                  "debitLines": [{"accountId": "%s", "amount": 100.00}],
                  "creditLines": [{"accountId": "%s", "amount": 100.00}]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private static final class RecordingPostLedgerEntryInputPort implements PostLedgerEntryInputPort {

        private PostLedgerEntryCommand command;

        @Override
        public PostingResult postLedgerEntry(PostLedgerEntryCommand command) {
            this.command = command;
            return new PostingResult(new LedgerEntryView(
                    UUID.randomUUID().toString(),
                    command.postingRequestId(),
                    command.description(),
                    command.currency(),
                    command.effectiveAt(),
                    Instant.parse("2026-07-03T10:15:30Z"),
                    null,
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    List.of()), false);
        }
    }

    private static final class NoOpGetLedgerEntryInputPort implements GetLedgerEntryInputPort {

        @Override
        public LedgerEntryView getLedgerEntry(com.digitalbank.ledgerservice.domain.model.LedgerEntryId ledgerEntryId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoOpPostLedgerReversalInputPort implements PostLedgerReversalInputPort {

        @Override
        public PostingResult reverseLedgerEntry(PostLedgerReversalCommand command) {
            throw new UnsupportedOperationException();
        }
    }
}
