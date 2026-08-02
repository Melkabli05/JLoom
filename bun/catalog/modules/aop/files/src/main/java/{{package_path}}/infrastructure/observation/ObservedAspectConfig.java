package {{package}}.infrastructure.observation;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring Boot's ObservedAutoConfiguration only wires the @Observed annotation engine when a
// user declares an ObservedAspect bean themselves — see Spring Boot 4's observability reference
// docs and Micrometer's @Observed guide. Declaring it here once, alongside the aspectj starter
// (already in merges/gradle.yml), is the entire integration cost.
@Configuration
class ObservedAspectConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
