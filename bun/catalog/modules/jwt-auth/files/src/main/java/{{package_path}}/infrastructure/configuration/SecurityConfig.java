package {{package}}.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    // No .csrf(disable) alternative is needed here: CSRF protection defends session-cookie-based
    // browser clients, where the browser attaches credentials automatically. This is a stateless
    // bearer-token API — the Authorization header is never attached automatically by a browser —
    // so CSRF doesn't apply, matching the official Spring Security guidance for non-browser/token
    // based APIs.
    //
    // No .headers(...) customization is needed either: HttpSecurity already applies Spring
    // Security's own secure defaults (X-Content-Type-Options: nosniff, X-Frame-Options: DENY,
    // Cache-Control: no-store, HSTS on HTTPS) with zero code required.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtDecoder jwtDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            CorsConfigurationSource corsConfigurationSource,
                                            AuthenticationEntryPoint authenticationEntryPoint,
                                            Environment environment) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                // Matches the actuator endpoints base already curates as safe to expose
                // (management.endpoints.web.exposure.include: health,info,metrics,prometheus) —
                // permitting the whole /actuator/** prefix rather than hand-picking a subset here
                // means a Prometheus scraper (which never presents a JWT) can always reach
                // whatever base decides to expose, without this module's allowlist silently
                // drifting out of sync with that decision.
                auth.requestMatchers("/actuator/**").permitAll();
                auth.requestMatchers("/.well-known/jwks.json").permitAll();
                for (String pathSpec : publicPaths(environment)) {
                    int separator = pathSpec.indexOf(':');
                    HttpMethod method = HttpMethod.valueOf(pathSpec.substring(0, separator));
                    String pattern = pathSpec.substring(separator + 1);
                    auth.requestMatchers(method, pattern).permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .jwt(jwt -> jwt
                            .decoder(jwtDecoder)
                            .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
    private static List<String> publicPaths(Environment environment) {
        Map<String, String> byModule = Binder.get(environment)
                .bind("jwt.public-paths", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        return byModule.values().stream()
                .flatMap(csv -> Arrays.stream(csv.split(",")))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.jwk-set-uri}") String jwkSetUri,
                                 @Value("${jwt.issuer}") String issuer,
                                 @Value("${jwt.audience:}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                // Explicit even though it's already Nimbus's own default for a JWK-Set-backed
                // decoder — algorithm confusion attacks (e.g. an attacker crafting an HS256 token
                // "signed" with the RSA public key as if it were an HMAC secret) rely on a
                // resource server trusting whatever alg the token claims. Pinning RS256 here means
                // that trick is rejected outright rather than relying on an implicit default that
                // could change.
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(buildValidator(issuer, audience));
        return decoder;
    }
    // JwtValidators.createDefaultWithIssuer(issuer) already composes JwtTimestampValidator +
    // JwtTypeValidator.jwt() + the issuer check — but its JwtTimestampValidator leaves
    // allowEmptyExpiryClaim at its (Spring Security 7) default of true, meaning a token with NO
    // exp claim at all is treated as never expiring. Built explicitly here instead so exp can be
    // made mandatory — every token this resource server accepts must actually expire. Audience
    // validation is added only when jwt.audience is configured (blank by default, matching this
    // module's other optional-by-default settings) since it requires the issuer to embed a
    // matching aud claim — see identity-service's JwtIssuer for the paired opt-in.
    private static OAuth2TokenValidator<Jwt> buildValidator(String issuer, String audience) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setAllowEmptyExpiryClaim(false);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>(List.of(
                timestampValidator,
                JwtTypeValidator.jwt(),
                new JwtIssuerValidator(issuer)));
        if (!audience.isBlank()) {
            validators.add(new JwtAudienceValidator(audience));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
    // Blank by default (no cross-origin browser access permitted) — this module can't guess a
    // real deployment's frontend origin(s), and "no CORS configured" is the secure default: it
    // only blocks browser-JS cross-origin calls (a server-to-server or curl/mobile client is
    // never subject to CORS, since it's a browser-enforced mechanism, not a server-side auth
    // check). Set jwt.cors.allowed-origins to the real frontend origin(s) to enable it.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${jwt.cors.allowed-origins:}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // No setAllowCredentials(true): this is a bearer-token API (Authorization header, not
        // cookies), so CORS "credentials" mode is never needed — and never combine it with a
        // wildcard origin, which browsers reject and which would otherwise be a real
        // misconfiguration risk.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return new ProblemDetailAuthenticationEntryPoint(objectMapper);
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Still Spring Security's own recommended default as of 7.1: bcrypt via
        // DelegatingPasswordEncoder, which also transparently upgrades/reads legacy-prefixed
        // hashes if the encoding scheme ever changes later.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
