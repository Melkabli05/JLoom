package {{package}}.notification.presentation.dto;

import {{package}}.notification.domain.model.NotificationStatus;
import java.util.UUID;
public record NotificationResponse(UUID id, NotificationStatus status) {
}
