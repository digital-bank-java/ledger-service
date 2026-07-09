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

        var postResponse = sendJson(
                "POST",
                "/internal/v1/ledger-entries",
                """
                {
                  "postingRequestId": "ledger-posting-001",
                  "description": "Settlement posting",
                  "currency": "AED",
                  "effectiveAt": "2026-07-03T09:00:00Z",
                  "debitLines": [
                    {
                      "accountId": "%s",
                      "amount": 125.50
                    }
                  ],
                  "creditLines": [
                    {
                      "accountId": "%s",
                      "amount": 125.50
                    }
                  ]
                }
                """
                        .formatted(debitAccountId, creditAccountId));

        assertThat(postResponse.statusCode()).isEqualTo(201);
        assertContentType(postResponse, "application/json");
        var posted = objectMapper.readTree(postResponse.body());
        assertThat(posted.path("ledgerEntryId").asText()).isNotBlank();
        assertThat(posted.path("postingRequestId").asText()).isEqualTo("ledger-posting-001");
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
                        .formatted(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/unbalanced-ledger-entry");
    }

    @Test
    void rejectsDuplicatePostingRequestId() throws Exception {
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
                .formatted(UUID.randomUUID(), UUID.randomUUID());

        var firstResponse = sendJson("POST", "/internal/v1/ledger-entries", requestBody);
        var secondResponse = sendJson("POST", "/internal/v1/ledger-entries", requestBody);

        assertThat(firstResponse.statusCode()).isEqualTo(201);
        assertThat(secondResponse.statusCode()).isEqualTo(409);
        assertContentType(secondResponse, "application/problem+json");
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .header("Accept", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
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
