package {{package}}.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
class JwksEndpoint {
    private final JWKSet jwkSet;
    JwksEndpoint(JWKSource<SecurityContext> jwkSource) {
        this.jwkSet = ((ImmutableJWKSet<SecurityContext>) jwkSource).getJWKSet();
    }
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> jwks() {
        return jwkSet.toJSONObject(true);
    }
}
