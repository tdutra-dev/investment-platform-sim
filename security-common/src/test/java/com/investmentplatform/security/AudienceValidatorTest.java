package com.investmentplatform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    private final AudienceValidator validator = new AudienceValidator("customer-service");

    private Jwt jwtWithAudience(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).subject("user");
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    void acceptsTokenWithRequiredAudience() {
        assertThat(validator.validate(jwtWithAudience(List.of("other", "customer-service"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokenForAnotherAudience() {
        assertThat(validator.validate(jwtWithAudience(List.of("transaction-service"))).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutAudience() {
        assertThat(validator.validate(jwtWithAudience(null)).hasErrors()).isTrue();
    }
}
