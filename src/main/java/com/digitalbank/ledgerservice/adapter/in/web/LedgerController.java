package com.digitalbank.ledgerservice.adapter.in.web;

import com.digitalbank.ledgerservice.application.port.in.GetLedgerEntryInputPort;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryCommand;
import com.digitalbank.ledgerservice.application.port.in.PostLedgerEntryInputPort;
import com.digitalbank.ledgerservice.domain.model.LedgerEntryId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ledger Entries")
class LedgerController {

    private static final String VALIDATION_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "errors": [
                {
                  "field": "debitLines[0].amount",
                  "message": "must be greater than or equal to 0.0001"
                }
              ]
            }
            """;

    private static final String UNBALANCED_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/unbalanced-ledger-entry",
              "title": "Invalid ledger entry",
              "status": 400,
              "detail": "total debit amount must equal total credit amount"
            }
            """;

    private static final String CONFLICT_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/ledger-posting-conflict",
              "title": "Ledger posting conflict",
              "status": 409,
              "detail": "Posting request conflicts with existing data",
              "postingRequestId": "ledger-posting-001"
            }
            """;

    private final PostLedgerEntryInputPort postLedgerEntryInputPort;
    private final GetLedgerEntryInputPort getLedgerEntryInputPort;

    LedgerController(
            PostLedgerEntryInputPort postLedgerEntryInputPort, GetLedgerEntryInputPort getLedgerEntryInputPort) {
        this.postLedgerEntryInputPort = postLedgerEntryInputPort;
        this.getLedgerEntryInputPort = getLedgerEntryInputPort;
    }

    @PostMapping("/internal/v1/ledger-entries")
    @Operation(summary = "Post a balanced ledger entry")
    @ApiResponse(
            responseCode = "201",
            description = "Ledger entry posted",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerEntryResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid request or unbalanced entry",
            content = {
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples =
                                @ExampleObject(
                                        name = "validation-error",
                                        summary = "Validation failure",
                                        value = VALIDATION_PROBLEM_EXAMPLE)),
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class),
                        examples =
                                @ExampleObject(
                                        name = "unbalanced-ledger-entry",
                                        summary = "Unbalanced ledger entry",
                                        value = UNBALANCED_PROBLEM_EXAMPLE))
            })
    @ApiResponse(
            responseCode = "409",
            description = "Posting request already exists",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "posting-conflict",
                                            summary = "Duplicate posting request",
                                            value = CONFLICT_PROBLEM_EXAMPLE)))
    ResponseEntity<LedgerEntryResponse> postLedgerEntry(@Valid @RequestBody PostLedgerEntryRequest request) {
        var view = postLedgerEntryInputPort.postLedgerEntry(new PostLedgerEntryCommand(
                request.postingRequestId(),
                request.description(),
                request.currency(),
                request.effectiveAt(),
                request.debitLines().stream()
                        .map(line -> new PostLedgerEntryCommand.Line(line.accountId(), line.amount()))
                        .toList(),
                request.creditLines().stream()
                        .map(line -> new PostLedgerEntryCommand.Line(line.accountId(), line.amount()))
                        .toList()));

        var response = LedgerEntryResponse.from(view);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/internal/v1/ledger-entries/" + response.ledgerEntryId()))
                .body(response);
    }

    @GetMapping("/internal/v1/ledger-entries/{ledgerEntryId}")
    @Operation(summary = "Get a ledger entry")
    @ApiResponse(
            responseCode = "200",
            description = "Ledger entry returned",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LedgerEntryResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Ledger entry not found",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<LedgerEntryResponse> getLedgerEntry(@PathVariable UUID ledgerEntryId) {
        var view = getLedgerEntryInputPort.getLedgerEntry(new LedgerEntryId(ledgerEntryId));
        return ResponseEntity.ok(LedgerEntryResponse.from(view));
    }
}
