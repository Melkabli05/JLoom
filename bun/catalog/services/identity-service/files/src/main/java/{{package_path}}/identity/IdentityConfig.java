package {{package}}.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration
class IdentityConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtIssuer jwtIssuer(@Value("${jwt.issuer:jloom-app}") String issuer,
                        @Value("${jwt.token-ttl-seconds:3600}") long ttl,
                        @Value("${jwt.secret:}") String secret,
                        Clock clock) {
        byte[] secretBytes = (secret == null || secret.isBlank())
                ? JwtIssuer.generateSecret()
                : secret.getBytes(StandardCharsets.UTF_8);
        return new JwtIssuer(secretBytes, issuer, ttl, clock);
    }
}