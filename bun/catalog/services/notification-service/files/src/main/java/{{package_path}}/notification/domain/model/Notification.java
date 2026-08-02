package {{package}}.notification.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Notification {
    @Id
    private UUID id;
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;
    @Column(nullable = false)
    private String subject;
    @Column(nullable = false)
    private String body;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "last_error", length = 40)
    @Enumerated(EnumType.STRING)
    private FailureReason lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "sent_at")
    private Instant sentAt;
    public void markSent(Instant sentAt) {
        this.status = NotificationStatus.SENT;
        this.sentAt = sentAt;
        this.attemptCount++;
        this.lastError = null;
    }
    public void markFailed(FailureReason reason) {
        this.status = NotificationStatus.FAILED;
        this.attemptCount++;
        this.lastError = reason;
    }
}
