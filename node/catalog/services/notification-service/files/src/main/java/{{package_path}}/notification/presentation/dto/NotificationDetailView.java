package {{package}}.notification.presentation.dto;

import {{package}}.notification.domain.model.FailureReason;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationDetailView(
        UUID id,
        String recipientEmail,
        String subject,
        String body,
        NotificationChannel channel,
        NotificationStatus status,
        int attemptCount,
        FailureReason lastError,
        Instant createdAt,
        Instant sentAt
) {
}
