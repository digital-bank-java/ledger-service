package com.digitalbank.ledgerservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtDecoderConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(JwtDecoderConfiguration.class);

    @Test
    void requiresIssuerAndSecretForHmacMode() {
        contextRunner
                .withPropertyValues("auth.jwt.secret=" + base64Secret())
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("auth.jwt.issuer must be configured"));
    }

    @Test
    void rejectsInvalidBase64Secret() {
        contextRunner
                .withPropertyValues("auth.jwt.issuer=digital-bank-auth", "auth.jwt.secret=not-base64")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("auth.jwt.secret must be valid base64"));
    }

    @Test
    void createsDecoderForValidBase64SecretAndIssuer() {
        contextRunner
                .withPropertyValues("auth.jwt.issuer=digital-bank-auth", "auth.jwt.secret=" + base64Secret())
                .run(context -> assertThat(context).hasSingleBean(JwtDecoder.class));
    }

    private static String base64Secret() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }
}
