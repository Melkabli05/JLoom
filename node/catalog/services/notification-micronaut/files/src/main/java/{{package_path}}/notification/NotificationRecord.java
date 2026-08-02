package {{package}}.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationRecord(
        UUID id,
        String channel,
        String recipient,
        String message,
        Instant createdAt
) {
}