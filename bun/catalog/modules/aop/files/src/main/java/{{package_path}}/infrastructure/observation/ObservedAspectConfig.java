package {{package}}.infrastructure.observation;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

// Spring Boot's ObservedAutoConfiguration only wires the @Observed annotation engine when a
// user declares an ObservedAspect bean themselves — see Spring Boot 4's observability reference
// docs and Micrometer's @Observed guide. Declaring it here once, alongside the aspectj starter
// (already in merges/gradle.yml), is the entire integration cost.
//
// @Order(Ordered.LOWEST_PRECEDENCE) per Spring's AOP best practices: lower precedence = the
// observation wraps the innermost layer, so any future user-defined aspects (security, audit,
// etc.) get to see the inner method's result/error and decorate the observation accordingly.
// Using LOWEST_PRECEDENCE (not a numeric value) keeps the intent explicit.
@Configuration
@Order(Ordered.LOWEST_PRECEDENCE)
class ObservedAspectConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
