package {{package}}.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
class MethodTimingAspect {

    private static final Logger log = LoggerFactory.getLogger(MethodTimingAspect.class);

    @Around("@within(org.springframework.stereotype.Service)")
    Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("{} took {}ms", joinPoint.getSignature().toShortString(), elapsedMs);
        }
    }
}
