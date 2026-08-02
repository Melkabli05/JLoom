package {{package}}.infrastructure.observation;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class LoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        if (!log.isDebugEnabled()) {
            return pjp.proceed();
        }
        String signature = pjp.getSignature().toShortString();
        log.debug(">> {} args={}", signature, Arrays.toString(pjp.getArgs()));
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            log.debug("<< {} elapsed={}ms", signature, (System.nanoTime() - start) / 1_000_000);
            return result;
        } catch (Throwable ex) {
            log.debug("!! {} elapsed={}ms", signature, (System.nanoTime() - start) / 1_000_000, ex);
            throw ex;
        }
    }
}
