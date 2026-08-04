package com.digitalbank.ledgerservice.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

record PostLedgerReversalRequest(
        @NotBlank String postingRequestId, @NotBlank String description, @NotNull Instant effectiveAt) {}
