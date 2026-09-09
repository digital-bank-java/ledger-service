package com.digitalbank.ledgerservice.configuration;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.posting.acceptance-fixture")
public class LedgerPostingAcceptanceFixtureProperties {

    private boolean enabled;
    private String postingRequestId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPostingRequestId() {
        return postingRequestId;
    }

    public void setPostingRequestId(String postingRequestId) {
        this.postingRequestId = postingRequestId == null ? "" : postingRequestId;
    }

    public boolean matchesPostingRequestId(String value) {
        return enabled && postingRequestId.equals(value);
    }

    void validateActivation(String[] activeProfiles, Map<String, String> processEnvironment) {
        if (!enabled) {
            return;
        }
        if (activeProfiles.length != 1 || !"sit".equals(activeProfiles[0])) {
            throw new IllegalStateException(
                    "Ledger posting acceptance fixture may only be enabled with exactly the sit profile");
        }
        if (!"true".equals(processEnvironment.get("LEDGER_POSTING_ACCEPTANCE_FIXTURE_ENABLED"))) {
            throw new IllegalStateException("Ledger posting acceptance fixture must be enabled by the process environment");
        }
        var processEnvironmentPostingRequestId =
                processEnvironment.get("LEDGER_POSTING_ACCEPTANCE_FIXTURE_POSTING_REQUEST_ID");
        if (processEnvironmentPostingRequestId == null
                || processEnvironmentPostingRequestId.isBlank()
                || !processEnvironmentPostingRequestId.equals(postingRequestId)) {
            throw new IllegalStateException(
                    "Ledger posting acceptance fixture request ID must match the process environment");
        }
    }
}
