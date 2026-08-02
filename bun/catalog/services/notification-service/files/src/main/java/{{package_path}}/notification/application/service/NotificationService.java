package {{package}}.notification.application.service;
import {{package}}.notification.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Duration;
import java.util.UUID;
public interface NotificationService {
    Notification submit(String recipientEmail, String subject, String body, String idempotencyKey);
    Notification getById(UUID id);
    Page<Notification> list(Pageable pageable);
    void redispatchStuckPending(Duration staleAfter);
}
