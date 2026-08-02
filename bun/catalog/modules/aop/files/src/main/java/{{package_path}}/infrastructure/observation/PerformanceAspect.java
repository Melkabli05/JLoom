package {{package}}.infrastructure.observation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
class PerformanceAspect {
    private static final Logger log = LoggerFactory.getLogger(PerformanceAspect.class);
    private final long thresholdMs;
    private final Counter slowCalls;
    PerformanceAspect(@Value("${jloom.performance.threshold-ms:500}") long thresholdMs,
                      MeterRegistry registry) {
        this.thresholdMs = thresholdMs;
        this.slowCalls = Counter.builder("jloom.performance.slow.calls")
                .description("Calls exceeding the configured jloom.performance.threshold-ms")
                .register(registry);
    }
    @Around("@within(org.springframework.stereotype.Service) || @within(org.springframework.web.bind.annotation.RestController)")
    Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (elapsedMs >= thresholdMs) {
                slowCalls.increment();
                log.warn("SLOW [{}ms >= {}ms threshold] {}",
                        elapsedMs, thresholdMs, pjp.getSignature().toShortString());
            }
        }
    }
}
