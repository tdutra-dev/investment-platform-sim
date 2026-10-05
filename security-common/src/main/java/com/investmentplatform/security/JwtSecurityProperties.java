package com.investmentplatform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code security.jwt.issuer}: expected {@code iss} claim (the URL clients use to reach the identity provider).
 * {@code security.jwt.jwk-set-uri}: where to fetch signing keys (can differ from the issuer, e.g. inside Docker).
 * {@code security.jwt.audience}: value that must be present in the {@code aud} claim.
 */
@ConfigurationProperties("security.jwt")
public record JwtSecurityProperties(String issuer, String jwkSetUri, String audience) {
}
