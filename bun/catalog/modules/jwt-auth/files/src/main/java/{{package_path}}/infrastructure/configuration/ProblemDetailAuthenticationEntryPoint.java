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
// A missing/invalid/expired bearer token never reaches the DispatcherServlet or any
// @RestControllerAdvice at all — ExceptionTranslationFilter intercepts it at the security-filter
// level and hands it to whichever AuthenticationEntryPoint is configured, well before Spring MVC's
// own exception-handling machinery ever runs. That's a different, earlier failure path than the
// 403/AccessDeniedException case SecurityExceptionHandler already covers (which IS thrown from
// inside handler invocation, so it does reach @RestControllerAdvice normally) — so a consistent,
// ProblemDetail-shaped body for 401s needs its own entry point, not another @ExceptionHandler.
//
// Delegates header/status composition to Spring Security's own BearerTokenAuthenticationEntryPoint
// (correct per RFC 6750 — sets WWW-Authenticate with the specific error/description/resource
// metadata, and the right status for the specific failure) rather than reimplementing it, then
// only adds the JSON body on top.
//
// Not a @Component: it's constructed by SecurityConfig's own @Bean method instead, alongside
// everything else SecurityConfig wires up — @WebMvcTest slices across this catalog already
// standardize on @Import(SecurityConfig.class) alone bringing in everything the security filter
// chain needs, and a separate @Component here wouldn't be picked up by that import.
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
        if (authException instanceof OAuth2AuthenticationException oauth2Exception
                && oauth2Exception.getError().getDescription() != null) {
            return oauth2Exception.getError().getDescription();
        }
        return "Full authentication is required to access this resource.";
    }
}
