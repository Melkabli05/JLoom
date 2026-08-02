package {{package}}.identity;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import java.time.Clock;
import java.time.Instant;
import java.util.List;





public class JwtIssuer {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlSeconds;
    private final Clock clock;
    public JwtIssuer(JwtEncoder encoder, String issuer, long ttlSeconds, Clock clock) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }
    public String issue(String subject) {
        return issue(subject, List.of());
    }
    public String issue(String subject, List<String> roles) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds));
        if (!roles.isEmpty()) {
            claims.claim("roles", roles);
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
