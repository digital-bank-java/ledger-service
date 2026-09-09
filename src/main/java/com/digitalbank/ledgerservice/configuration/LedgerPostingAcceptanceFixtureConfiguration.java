package com.digitalbank.ledgerservice.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LedgerPostingAcceptanceFixtureProperties.class)
class LedgerPostingAcceptanceFixtureConfiguration {

    private final LedgerPostingAcceptanceFixtureProperties properties;
    private final Environment environment;

    LedgerPostingAcceptanceFixtureConfiguration(
            LedgerPostingAcceptanceFixtureProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void validateActiveProfiles() {
        properties.validateActivation(environment.getActiveProfiles(), System.getenv());
    }
}
