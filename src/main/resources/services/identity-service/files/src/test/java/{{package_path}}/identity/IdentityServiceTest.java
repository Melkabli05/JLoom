package {{package}}.identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityServiceTest {

    @Test
    void issueThenVerifyRoundTrips() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        JwtIssuer issuer = new JwtIssuer(JwtIssuer.generateSecret(), "test-issuer", 3600, fixed);

        String token = issuer.issue("alice");
        assertNotNull(token);

        String subject = issuer.verify(token);
        assertEquals("alice", subject);
    }

    @Test
    void verifyRejectsTamperedToken() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        JwtIssuer issuer = new JwtIssuer(JwtIssuer.generateSecret(), "test-issuer", 3600, fixed);

        String token = issuer.issue("alice");
        int lastIdx = token.length() - 1;
        char flipped = token.charAt(lastIdx) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, lastIdx) + flipped;

        assertThrows(IllegalArgumentException.class, () -> issuer.verify(tampered));
    }

    @Test
    void verifyRejectsExpiredToken() {
        Clock start = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        byte[] secret = JwtIssuer.generateSecret();
        String token = new JwtIssuer(secret, "test-issuer", 60, start).issue("alice");

        Clock later = Clock.fixed(start.instant().plusSeconds(120), ZoneId.of("UTC"));
        JwtIssuer laterIssuer = new JwtIssuer(secret, "test-issuer", 60, later);

        assertThrows(IllegalArgumentException.class, () -> laterIssuer.verify(token));
    }

    @Test
    void issueWithRolesEmbedsARolesClaim() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        JwtIssuer issuer = new JwtIssuer(JwtIssuer.generateSecret(), "test-issuer", 3600, fixed);

        String token = issuer.issue("11111111-1111-1111-1111-111111111111", List.of("ADMIN"));
        assertEquals("11111111-1111-1111-1111-111111111111", issuer.verify(token));

        String payloadB64 = token.split("\\.")[1];
        String payloadJson = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        assertTrue(payloadJson.contains("\"roles\":[\"ADMIN\"]"));
    }

    @Test
    void issueWithoutRolesOmitsTheRolesClaim() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        JwtIssuer issuer = new JwtIssuer(JwtIssuer.generateSecret(), "test-issuer", 3600, fixed);

        String token = issuer.issue("alice");

        String payloadB64 = token.split("\\.")[1];
        String payloadJson = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
        assertTrue(!payloadJson.contains("roles"));
    }

    @Test
    void shortSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new JwtIssuer(new byte[16], "test", 60, Clock.systemUTC()));
    }
}