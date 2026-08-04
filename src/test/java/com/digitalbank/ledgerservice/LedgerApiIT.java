package com.digitalbank.ledgerservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LedgerApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @LocalServerPort
    private int port;

    @Test
    void postsAndRetrievesLedgerEntry() throws Exception {
        var debitAccountId = UUID.randomUUID();
        var creditAccountId = UUID.randomUUID();
        var postingRequestId = uniqueRequestId("ledger-posting-001");

        var requestBody = """
                {
                  "postingRequestId": "ledger-posting-001",
                  "description": "Settlement posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "debitLines": [{"accountId": "%s", "amount": 125.50}],
                  "creditLines": [{"accountId": "%s", "amount": 125.50}]
                }
                """.formatted(debitAccountId, creditAccountId)
                .replace("ledger-posting-001", postingRequestId);
        var postResponse = sendJson(
                "POST",
                "/internal/v1/ledger-entries",
                requestBody);

        assertThat(postResponse.statusCode()).isEqualTo(201);
        assertContentType(postResponse, "application/json");
        var posted = objectMapper.readTree(postResponse.body());
        assertThat(posted.path("ledgerEntryId").asText()).isNotBlank();
        assertThat(posted.path("postingRequestId").asText()).isEqualTo(postingRequestId);
        assertThat(posted.path("totalDebitAmount").decimalValue()).isEqualByComparingTo("125.50");
        assertThat(posted.path("totalCreditAmount").decimalValue()).isEqualByComparingTo("125.50");
        assertThat(posted.path("lines")).hasSize(2);

        var getResponse = send("GET", "/internal/v1/ledger-entries/" + posted.path("ledgerEntryId").asText());

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertContentType(getResponse, "application/json");
        var loaded = objectMapper.readTree(getResponse.body());
        assertThat(loaded.path("ledgerEntryId").asText()).isEqualTo(posted.path("ledgerEntryId").asText());
    }

    @Test
    void rejectsUnbalancedLedgerEntry() throws Exception {
        var response = sendJson(
                "POST",
                "/internal/v1/ledger-entries",
                """
                {
                  "postingRequestId": "ledger-posting-002",
                  "description": "Unbalanced posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "debitLines": [
                    {
                      "accountId": "%s",
                      "amount": 100.00
                    }
                  ],
                  "creditLines": [
                    {
                      "accountId": "%s",
                      "amount": 90.00
                    }
                  ]
                }
                """
                        .formatted(UUID.randomUUID(), UUID.randomUUID())
                        .replace("ledger-posting-002", uniqueRequestId("ledger-posting-002")));

        assertThat(response.statusCode()).isEqualTo(400);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/unbalanced-ledger-entry");
    }

    @Test
    void replaysExactDuplicatePostingRequestId() throws Exception {
        var requestBody = """
                {
                  "postingRequestId": "ledger-posting-003",
                  "description": "Duplicate posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "debitLines": [
                    {
                      "accountId": "%s",
                      "amount": 100.00
                    }
                  ],
                  "creditLines": [
                    {
                      "accountId": "%s",
                      "amount": 100.00
                    }
                  ]
                }
                """
                .formatted(UUID.randomUUID(), UUID.randomUUID())
                .replace("ledger-posting-003", uniqueRequestId("ledger-posting-003"));

        var firstResponse = sendJson("POST", "/internal/v1/ledger-entries", requestBody);
        var secondResponse = sendJson("POST", "/internal/v1/ledger-entries", requestBody);

        assertThat(firstResponse.statusCode()).isEqualTo(201);
        assertThat(secondResponse.statusCode()).isEqualTo(200);
        assertThat(secondResponse.headers().firstValue("Idempotent-Replay")).hasValue("true");
        assertThat(objectMapper.readTree(secondResponse.body()).path("ledgerEntryId").asText())
                .isEqualTo(objectMapper.readTree(firstResponse.body()).path("ledgerEntryId").asText());
    }

    @Test
    void rejectsSamePostingRequestIdWithChangedPayload() throws Exception {
        var requestBody = """
                {
                  "postingRequestId": "ledger-posting-conflict",
                  "description": "Original posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "debitLines": [{"accountId": "%s", "amount": 100.00}],
                  "creditLines": [{"accountId": "%s", "amount": 100.00}]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID())
                .replace("ledger-posting-conflict", uniqueRequestId("ledger-posting-conflict"));
        var changedBody = requestBody.replace("Original posting", "Changed posting");

        assertThat(sendJson("POST", "/internal/v1/ledger-entries", requestBody).statusCode()).isEqualTo(201);
        var conflict = sendJson("POST", "/internal/v1/ledger-entries", changedBody);

        assertThat(conflict.statusCode()).isEqualTo(409);
        assertContentType(conflict, "application/problem+json");
    }

    @Test
    void createsAndReplaysAppendOnlyReversal() throws Exception {
        var sourceRequestId = uniqueRequestId("ledger-posting-reversal-source");
        var sourceResponse = sendJson(
                "POST",
                "/internal/v1/ledger-entries",
                """
                {
                  "postingRequestId": "ledger-posting-reversal-source",
                  "description": "Original posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "debitLines": [{"accountId": "%s", "amount": 100.00}],
                  "creditLines": [{"accountId": "%s", "amount": 100.00}]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID())
                .replace("ledger-posting-reversal-source", sourceRequestId));
        assertThat(sourceResponse.statusCode()).isEqualTo(201);
        var sourceId = objectMapper.readTree(sourceResponse.body()).path("ledgerEntryId").asText();
        var reversalBody = """
                {
                  "postingRequestId": "ledger-reversal-001",
                  "description": "Reverse incorrect posting",
                  "effectiveAt": "2026-07-03T10:00:00Z"
                }
                """;

        var reversal = sendJson("POST", "/internal/v1/ledger-entries/" + sourceId + "/reversals", reversalBody);
        var replay = sendJson("POST", "/internal/v1/ledger-entries/" + sourceId + "/reversals", reversalBody);

        assertThat(reversal.statusCode()).isEqualTo(201);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.headers().firstValue("Idempotent-Replay")).hasValue("true");
        var reversed = objectMapper.readTree(reversal.body());
        assertThat(reversed.path("reversalOfLedgerEntryId").asText()).isEqualTo(sourceId);
        assertThat(reversed.path("lines").findValuesAsText("lineType"))
                .containsExactlyInAnyOrder("DEBIT", "CREDIT");
    }

    @Test
    void concurrentIdenticalPostingsCreateOneEntry() throws Exception {
        var requestBody = """
                {
                  "postingRequestId": "%s",
                  "description": "Concurrent posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "debitLines": [{"accountId": "%s", "amount": 100.00}],
                  "creditLines": [{"accountId": "%s", "amount": 100.00}]
                }
                """.formatted(uniqueRequestId("ledger-concurrent"), UUID.randomUUID(), UUID.randomUUID());
        var requests = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return sendJson("POST", "/internal/v1/ledger-entries", requestBody);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }))
                .toList();

        var responses = requests.stream().map(java.util.concurrent.CompletableFuture::join).toList();
        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(
                201, 200, 200, 200, 200, 200, 200, 200);
        var ids = responses.stream()
                .map(response -> read(response.body()).path("ledgerEntryId").asText())
                .distinct()
                .toList();
        assertThat(ids).hasSize(1);
    }

    @Test
    void publishesExplicitOpenApiMetadata() throws Exception {
        var response = send("GET", "/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        var document = objectMapper.readTree(response.body());
        assertThat(document.path("info").path("title").asText()).isEqualTo("Digital Bank Ledger Service API");
        assertThat(document.path("info").path("version").asText()).isEqualTo("1.0.0");
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .header("Accept", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String uniqueRequestId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private com.fasterxml.jackson.databind.JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new AssertionError("Expected a JSON response", exception);
        }
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(method, HttpRequest.BodyPublishers.ofString(body))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void assertContentType(HttpResponse<String> response, String expectedPrefix) {
        assertThat(response.headers().firstValue("content-type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith(expectedPrefix));
    }
}
