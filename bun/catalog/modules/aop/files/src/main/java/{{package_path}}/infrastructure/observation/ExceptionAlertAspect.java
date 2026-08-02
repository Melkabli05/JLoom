package {{package}}.infrastructure.observation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class ExceptionAlertAspect {
    private static final Logger log = LoggerFactory.getLogger(ExceptionAlertAspect.class);
    private static final Set<Class<? extends Throwable>> EXPECTED =
            Set.of(
                    AccessDeniedException.class,
                    ResponseStatusException.class,
                    IllegalArgumentException.class,
                    org.springframework.web.bind.MethodArgumentNotValidException.class,
                    org.springframework.http.converter.HttpMessageNotReadableException.class,
                    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);

    private final Counter alerts;
    private final Counter suppressed;

    ExceptionAlertAspect(MeterRegistry registry) {
        this.alerts = Counter.builder("jloom.exception.alerts")
                .description("Unexpected exceptions escaping jloom-managed code (genuine production errors)")
                .tag("kind", "unexpected")
                .register(registry);
        this.suppressed = Counter.builder("jloom.exception.suppressed")
                .description("Expected business exceptions logged at DEBUG instead of ERROR")
                .tag("kind", "expected")
                .register(registry);
    }

    @AfterThrowing(
            pointcut = "@within(org.springframework.stereotype.Service) || @within(org.springframework.web.bind.annotation.RestController)",
            throwing = "ex")
    void alert(JoinPoint jp, Throwable ex) {
        if (isExpected(ex)) {
            suppressed.increment();
            log.debug("Expected exception in {}: {}", jp.getSignature().toShortString(), ex.getMessage());
            return;
        }
        alerts.increment();
        int status = statusFromException(ex);
        log.error("ALERT [{}] {}: {}", status, jp.getSignature().toShortString(), ex.getMessage(), ex);
    }

    private static boolean isExpected(Throwable ex) {
        for (Class<? extends Throwable> klass : EXPECTED) {
            if (klass.isInstance(ex)) {
                return true;
            }
        }
        Throwable current = ex.getCause();
        while (current != null && current != current.getCause()) {
            for (Class<? extends Throwable> klass : EXPECTED) {
                if (klass.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static int statusFromException(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            return status != null ? status.value() : 500;
        }
        if (ex instanceof org.springframework.web.bind.MethodArgumentNotValidException) return 400;
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException) return 400;
        if (ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) return 400;
        return 500;
    }
}
