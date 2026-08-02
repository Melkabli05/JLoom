package {{package}}.notification.infrastructure.configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.mail.MailSendException;
import java.time.Clock;
import java.time.Duration;
@Configuration
@EnableConfigurationProperties(NotificationRecoveryProperties.class)
class NotificationConfig {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }
    @Bean
    RetryTemplate emailDeliveryRetryTemplate() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .includes(MailSendException.class)
                .maxRetries(3)
                .delay(Duration.ofMillis(500))
                .multiplier(2.0)
                .jitter(Duration.ofMillis(100))
                .maxDelay(Duration.ofSeconds(5))
                .build();
        return new RetryTemplate(retryPolicy);
    }
}
