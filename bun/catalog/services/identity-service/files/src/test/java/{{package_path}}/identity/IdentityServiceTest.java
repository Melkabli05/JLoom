package {{package}}.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
class IdentityServiceTest {
    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
    private static JwtEncoder encoderFor(KeyPair keyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }
    private static JwtDecoder decoderFor(KeyPair keyPair) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    }
    @Test
    void issueThenDecodeRoundTrips() throws Exception {
        KeyPair keyPair = generateKeyPair();
        JwtIssuer issuer = new JwtIssuer(encoderFor(keyPair), "test-issuer", 3600, Clock.systemUTC());
        String token = issuer.issue("alice");
        Jwt decoded = decoderFor(keyPair).decode(token);
        assertEquals("alice", decoded.getSubject());



        assertEquals("test-issuer", decoded.getClaimAsString("iss"));
    }
    @Test
    void decodeRejectsTokenSignedByADifferentKey() throws Exception {
        KeyPair signingKeyPair = generateKeyPair();
        KeyPair otherKeyPair = generateKeyPair();
        JwtIssuer issuer = new JwtIssuer(encoderFor(signingKeyPair), "test-issuer", 3600, Clock.systemUTC());
        String token = issuer.issue("alice");
        assertThrows(JwtException.class, () -> decoderFor(otherKeyPair).decode(token));
    }
    @Test
    void issueWithRolesEmbedsARolesClaim() throws Exception {
        KeyPair keyPair = generateKeyPair();
        JwtIssuer issuer = new JwtIssuer(encoderFor(keyPair), "test-issuer", 3600, Clock.systemUTC());
        String token = issuer.issue("11111111-1111-1111-1111-111111111111", List.of("ADMIN"));
        Jwt decoded = decoderFor(keyPair).decode(token);
        assertEquals(List.of("ADMIN"), decoded.getClaimAsStringList("roles"));
    }
    @Test
    void issueWithoutRolesOmitsTheRolesClaim() throws Exception {
        KeyPair keyPair = generateKeyPair();
        JwtIssuer issuer = new JwtIssuer(encoderFor(keyPair), "test-issuer", 3600, Clock.systemUTC());
        String token = issuer.issue("alice");
        Jwt decoded = decoderFor(keyPair).decode(token);
        assertNull(decoded.getClaimAsStringList("roles"));
    }
}
