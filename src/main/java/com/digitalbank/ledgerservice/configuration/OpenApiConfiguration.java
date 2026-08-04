package com.digitalbank.ledgerservice.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {

    @Bean
    OpenAPI ledgerServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Digital Bank Ledger Service API")
                .version("1.0.0")
                .description("Internal API for immutable double-entry ledger postings and append-only reversals."));
    }
}
