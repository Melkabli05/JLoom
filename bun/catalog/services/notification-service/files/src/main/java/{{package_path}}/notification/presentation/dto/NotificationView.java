package {{package}}.notification.presentation.dto;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.domain.model.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
public record NotificationView(
        UUID id,
        String recipientEmail,
        String subject,
        NotificationChannel channel,
        NotificationStatus status,
        Instant createdAt,
        Instant sentAt
) {
}
