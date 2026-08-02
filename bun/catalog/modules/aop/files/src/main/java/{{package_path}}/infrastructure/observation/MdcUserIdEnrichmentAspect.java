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


        if (ANONYMOUS_PRINCIPAL.equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }
}
