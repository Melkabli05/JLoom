package {{package}}.infrastructure.observation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

// Pushes the authenticated principal's name into SLF4J's MDC as 'userId' for the duration of
// any @RestController method call, so every log line emitted while handling a request carries
// the userId alongside the traceId/spanId that base's log pattern already exposes. Anonymous
// requests (no principal) gracefully degrade to no userId key in MDC — the %X{userId:-}
// placeholder in base's log pattern renders an empty segment, no error.
//
// Spring AOP best practices applied:
// - @Order(HIGHEST_PRECEDENCE) so this aspect wraps outermost — runs before any other advice
//   (ObservedAspect etc.) so the MDC value is set when those downstream aspects execute and
//   their logs carry userId too.
// - Always use try/finally (not try/catch) so MDC.remove() runs even on exception.
// - pjp.proceed() throws Throwable — the aspect signature must match (no narrower Exception).
// - Single, narrow pointcut: @within(RestController), not a wildcard execution(* ..).
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcUserIdEnrichmentAspect {

    private static final String MDC_KEY = "userId";
    private static final String ANONYMOUS_PRINCIPAL = "anonymousUser";

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    Object enrichUserId(ProceedingJoinPoint pjp) throws Throwable {
        String userId = currentUserId();
        if (userId == null) {
            return pjp.proceed();
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, userId);
        try {
            return pjp.proceed();
        } finally {
            if (previous != null) {
                MDC.put(MDC_KEY, previous);
            } else {
                MDC.remove(MDC_KEY);
            }
        }
    }

    private static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        // Spring Security assigns the literal "anonymousUser" to anonymous requests — don't
        // surface it as a real user id; let MDC stay empty for those.
        if (ANONYMOUS_PRINCIPAL.equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }
}
