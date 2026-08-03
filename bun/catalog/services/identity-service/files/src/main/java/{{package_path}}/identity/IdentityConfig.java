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
