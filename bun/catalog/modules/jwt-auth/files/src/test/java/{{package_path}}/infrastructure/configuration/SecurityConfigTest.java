package {{package}}.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
class SecurityConfigTest {
    @Test
    void rejectsATokenWithNoExpiryClaim() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfig.buildValidator("test-issuer", "");
        Jwt jwt = jwtWithClaims(Map.of("iss", "test-issuer", "sub", "alice"));
        assertTrue(validator.validate(jwt).hasErrors());
    }
    @Test
    void acceptsAValidTokenWithNoAudienceConfigured() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfig.buildValidator("test-issuer", "");
        Jwt jwt = jwtWithClaims(Map.of("iss", "test-issuer", "sub", "alice", "exp", Instant.now().plusSeconds(3600)));
        assertFalse(validator.validate(jwt).hasErrors());
    }
    @Test
    void rejectsATokenFromAnUntrustedIssuer() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfig.buildValidator("test-issuer", "");
        Jwt jwt = jwtWithClaims(Map.of("iss", "someone-else", "sub", "alice", "exp", Instant.now().plusSeconds(3600)));
        assertTrue(validator.validate(jwt).hasErrors());
    }
    @Test
    void rejectsATokenWithTheWrongAudienceWhenAudienceIsConfigured() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfig.buildValidator("test-issuer", "my-resource-server");
        Jwt jwt = jwtWithClaims(Map.of(
                "iss", "test-issuer", "sub", "alice",
                "exp", Instant.now().plusSeconds(3600),
                "aud", List.of("someone-else")));
        assertTrue(validator.validate(jwt).hasErrors());
    }
    @Test
    void acceptsATokenWithTheConfiguredAudience() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfig.buildValidator("test-issuer", "my-resource-server");
        Jwt jwt = jwtWithClaims(Map.of(
                "iss", "test-issuer", "sub", "alice",
                "exp", Instant.now().plusSeconds(3600),
                "aud", List.of("my-resource-server")));
        assertFalse(validator.validate(jwt).hasErrors());
    }
    @Test
    void acceptsATokenWithNoAudienceClaimWhenNoAudienceIsConfigured() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfig.buildValidator("test-issuer", "");
        Jwt jwt = jwtWithClaims(Map.of(
                "iss", "test-issuer", "sub", "alice",
                "exp", Instant.now().plusSeconds(3600),
                "aud", List.of("anything")));
        assertFalse(validator.validate(jwt).hasErrors());
    }
    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .build();
    }
}
