package {{package}}.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
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
import java.util.UUID;

@Configuration
class IdentityConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    // If no key pair is configured, generate an ephemeral RSA-2048 pair at boot — the same
    // "auto-generate a dev-only default, real deployments must configure their own" fallback
    // this module already used for its old HMAC secret. A single-instance/dev setup works
    // immediately; multi-instance production deployments must configure a real, persistent key
    // pair (jwt.private-key/jwt.public-key, PEM-encoded — parsed via Spring Security's own
    // RsaKeyConverters, not hand-rolled) so every instance serves the same JWKS.
    @Bean
    JWKSource<SecurityContext> jwkSource(@Value("${jwt.private-key:}") String privateKeyPem,
                                         @Value("${jwt.public-key:}") String publicKeyPem) throws NoSuchAlgorithmException {
        KeyPair keyPair = (privateKeyPem.isBlank() || publicKeyPem.isBlank())
                ? generateEphemeralKeyPair()
                : loadConfiguredKeyPair(privateKeyPem, publicKeyPem);
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
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
                        Clock clock) {
        return new JwtIssuer(encoder, issuer, ttl, clock);
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
