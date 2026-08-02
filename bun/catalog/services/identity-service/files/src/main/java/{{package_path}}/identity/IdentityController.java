package {{package}}.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
class IdentityController {

    private static final Logger log = LoggerFactory.getLogger(IdentityController.class);

    private final JwtIssuer issuer;
    private final RestClient restClient;
    private final String userServiceBaseUrl;
    private final String internalServiceKey;

    IdentityController(JwtIssuer issuer,
                        @Value("${user-service.base-url:}") String userServiceBaseUrl,
                        @Value("${internal.service-key:}") String internalServiceKey) {
        this.issuer = issuer;
        this.userServiceBaseUrl = userServiceBaseUrl;
        this.internalServiceKey = internalServiceKey;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @PostMapping("/tokens")
    TokenResponse token(@RequestBody TokenRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (userServiceBaseUrl.isBlank()) {
            return new TokenResponse(issuer.issue(request.username()));
        }
        VerifiedPrincipal verified = verifyAgainstUserService(request.username(), request.password());
        if (verified == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials");
        }
        return new TokenResponse(issuer.issue(verified.id().toString(), List.of(verified.role())));
    }

    private VerifiedPrincipal verifyAgainstUserService(String email, String password) {
        try {
            return restClient.post()
                    .uri(userServiceBaseUrl + "/internal/users/verify-credentials")
                    .header("X-Internal-Service-Key", internalServiceKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new VerifyCredentialsRequestBody(email, password))
                    .retrieve()
                    .body(VerifiedPrincipal.class);
        } catch (RestClientException e) {
            log.warn("Credential verification against user-service failed", e);
            return null;
        }
    }

    // Authentication/401 handling is entirely jwt-auth's SecurityConfig now (.anyRequest()
    // .authenticated(), validated against this same service's own published JWKS) — no manual
    // header parsing or token verification left here.
    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(jwt.getSubject());
    }

    public record TokenRequest(String username, String password) {
    }

    public record TokenResponse(String token) {
    }

    public record MeResponse(String subject) {
    }

    private record VerifyCredentialsRequestBody(String email, String password) {
    }

    private record VerifiedPrincipal(UUID id, String role) {
    }
}
