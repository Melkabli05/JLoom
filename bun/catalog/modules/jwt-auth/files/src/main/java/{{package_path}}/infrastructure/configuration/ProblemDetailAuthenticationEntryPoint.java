package {{package}}.infrastructure.configuration;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final AuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;
    ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        delegate.commence(request, response, authException);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.valueOf(response.getStatus()));
        problem.setTitle("Unauthorized");
        problem.setDetail(detailFor(authException));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
    private static String detailFor(AuthenticationException authException) {
        return switch (authException) {
            case OAuth2AuthenticationException oauth2Exception when oauth2Exception.getError().getDescription() != null ->
                oauth2Exception.getError().getDescription();
            default -> "Full authentication is required to access this resource.";
        };
    }
}
