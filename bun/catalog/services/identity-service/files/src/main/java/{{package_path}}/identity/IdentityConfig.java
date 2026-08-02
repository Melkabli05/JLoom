package {{package}}.identity;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;

@Configuration
class IdentityConfig {

    private static final Logger log = LoggerFactory.getLogger(IdentityConfig.class);

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(@Value("${jwt.private-key:}") String privateKeyPem,
                                         @Value("${jwt.public-key:}") String publicKeyPem)
            throws NoSuchAlgorithmException, JOSEException {
        KeyPair keyPair;
        if (privateKeyPem.isBlank() || publicKeyPem.isBlank()) {
            log.warn("No jwt.private-key/jwt.public-key configured — generating an EPHEMERAL RSA "
                    + "key pair for this instance. Every previously issued token becomes invalid "
                    + "on restart, and this is unsafe behind more than one replica. Configure a "
                    + "persistent key pair before running this in production.");
            keyPair = generateEphemeralKeyPair();
        } else {
            keyPair = loadConfiguredKeyPair(privateKeyPem, publicKeyPem);
        }
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                // Deterministic (RFC 7638 JWK thumbprint, derived from the key's own modulus/
                // exponent), not a fresh UUID per boot — a random kid meant a persistently
                // configured key pair still silently invalidated every outstanding token on every
                // restart, since the published JWKS would only ever contain the new random kid,
                // never the previous one a not-yet-expired token was signed with. Confirmed live:
                // restarting with the exact same configured PEM key pair, a still-valid token
                // from the prior boot failed decode with "no matching key(s) found" until this
                // fix. A genuinely different (e.g. ephemeral, regenerated-per-boot) key still
                // gets a different kid too, since the thumbprint depends on the key material.
                .keyIDFromThumbprint()
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtIssuer jwtIssuer(JwtEncoder encoder,
                        @Value("${jwt.issuer:jloom-app}") String issuer,
                        @Value("${jwt.token-ttl-seconds:3600}") long ttl,
                        Clock clock,
                        @Value("${jwt.audience:}") String audience) {
        return new JwtIssuer(encoder, issuer, ttl, clock, audience);
    }

    private static KeyPair generateEphemeralKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
    private static KeyPair loadConfiguredKeyPair(String privateKeyPem, String publicKeyPem) {
        RSAPrivateKey privateKey = RsaKeyConverters.pkcs8()
                .convert(new ByteArrayInputStream(privateKeyPem.getBytes(StandardCharsets.UTF_8)));
        RSAPublicKey publicKey = RsaKeyConverters.x509()
                .convert(new ByteArrayInputStream(publicKeyPem.getBytes(StandardCharsets.UTF_8)));
        return new KeyPair(publicKey, privateKey);
    }
}
