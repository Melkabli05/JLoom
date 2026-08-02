package {{package}}.notification.infrastructure.configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@ConfigurationProperties(prefix = "notification.recovery")
public record NotificationRecoveryProperties(Duration staleAfter) {
    public NotificationRecoveryProperties {
        if (staleAfter == null) {
            staleAfter = Duration.ofMinutes(2);
        }
    }
}
