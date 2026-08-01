package {{package}}.notification.infrastructure.persistence;

import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.domain.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    List<Notification> findByStatusAndCreatedAtBefore(NotificationStatus status, Instant cutoff);
}
