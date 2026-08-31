package com.digitalbank.ledgerservice.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record PostLedgerEntryRequest(
        @NotBlank String postingRequestId,
        @NotBlank String description,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull Instant effectiveAt,
        String transactionId,
        String reservationRequestId,
        @NotEmpty List<@Valid LineRequest> debitLines,
        @NotEmpty List<@Valid LineRequest> creditLines) {

    record LineRequest(
            @NotNull UUID accountId,
            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount) {}
}
