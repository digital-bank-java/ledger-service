package com.digitalbank.ledgerservice.configuration;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerPostingAcceptanceFixturePropertiesTest {

    @Test
    void rejectsEnabledAcceptanceFixtureWhenSitIsNotTheOnlyActiveProfile() {
        var properties = enabledProperties("posting-request-001");

        assertThatIllegalStateException()
                .isThrownBy(() -> properties.validateActivation(
                        new String[] {"sit", "prod"}, fixtureEnvironment("posting-request-001")))
                .withMessage("Ledger posting acceptance fixture may only be enabled with exactly the sit profile");
    }

    @Test
    void rejectsEnabledAcceptanceFixtureWithoutProcessEnvironmentEnablement() {
        var properties = enabledProperties("posting-request-001");

        assertThatIllegalStateException()
                .isThrownBy(() -> properties.validateActivation(new String[] {"sit"}, Map.of(
                        "LEDGER_POSTING_ACCEPTANCE_FIXTURE_POSTING_REQUEST_ID", "posting-request-001")))
                .withMessage("Ledger posting acceptance fixture must be enabled by the process environment");
    }

    @Test
    void rejectsEnabledAcceptanceFixtureWhenProcessEnvironmentRequestIdDoesNotMatch() {
        var properties = enabledProperties("posting-request-001");

        assertThatIllegalStateException()
                .isThrownBy(() -> properties.validateActivation(
                        new String[] {"sit"}, fixtureEnvironment("posting-request-002")))
                .withMessage("Ledger posting acceptance fixture request ID must match the process environment");
    }

    @Test
    void rejectsEnabledAcceptanceFixtureWithBlankProcessEnvironmentRequestId() {
        var properties = enabledProperties("");

        assertThatIllegalStateException()
                .isThrownBy(() -> properties.validateActivation(new String[] {"sit"}, fixtureEnvironment("")))
                .withMessage("Ledger posting acceptance fixture request ID must match the process environment");
    }

    private static LedgerPostingAcceptanceFixtureProperties enabledProperties(String postingRequestId) {
        var properties = new LedgerPostingAcceptanceFixtureProperties();
        properties.setEnabled(true);
        properties.setPostingRequestId(postingRequestId);
        return properties;
    }

    private static Map<String, String> fixtureEnvironment(String postingRequestId) {
        return Map.of(
                "LEDGER_POSTING_ACCEPTANCE_FIXTURE_ENABLED", "true",
                "LEDGER_POSTING_ACCEPTANCE_FIXTURE_POSTING_REQUEST_ID", postingRequestId);
    }
}
