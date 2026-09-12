package com.digitalbank.ledgerservice.security;

import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(Environment environment) {
        String issuerUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        String jwkSetUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");

        if (issuerUri != null && !issuerUri.isBlank()) {
            if (jwkSetUri != null && !jwkSetUri.isBlank()) {
                var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
                decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
                return decoder;
            }

            return JwtDecoders.fromIssuerLocation(issuerUri);
        }

        String secret = requiredProperty(environment, "auth.jwt.secret");
        String issuer = requiredProperty(environment, "auth.jwt.issuer");
        byte[] decodedSecret = decodeSecret(secret);
        MacAlgorithm macAlgorithm = macAlgorithmFor(decodedSecret.length);
        var decoder = NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(decodedSecret, jcaAlgorithmFor(macAlgorithm)))
                .macAlgorithm(macAlgorithm)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    private static String requiredProperty(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured");
        }
        return value;
    }

    private static byte[] decodeSecret(String encodedSecret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedSecret);
            if (decoded.length < 32) {
                throw new IllegalStateException("auth.jwt.secret must decode to at least 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("auth.jwt.secret must be valid base64", exception);
        }
    }

    private static MacAlgorithm macAlgorithmFor(int secretLength) {
        if (secretLength >= 64) {
            return MacAlgorithm.HS512;
        }
        if (secretLength >= 48) {
            return MacAlgorithm.HS384;
        }
        return MacAlgorithm.HS256;
    }

    private static String jcaAlgorithmFor(MacAlgorithm macAlgorithm) {
        return switch (macAlgorithm) {
            case HS512 -> "HmacSHA512";
            case HS384 -> "HmacSHA384";
            case HS256 -> "HmacSHA256";
        };
    }
}
