package {{package}}.notification.application.service;

import {{package}}.notification.domain.model.FailureReason;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.infrastructure.persistence.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.UUID;
@Component
class EmailNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);
    private final MailSender mailSender;
    private final NotificationRepository repository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final RetryTemplate retryTemplate;
    EmailNotificationSender(MailSender mailSender, NotificationRepository repository, Clock clock,
                             MeterRegistry meterRegistry, RetryTemplate emailDeliveryRetryTemplate) {
        this.mailSender = mailSender;
        this.repository = repository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.retryTemplate = emailDeliveryRetryTemplate;
    }
    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }
    @Override
    @Async
    public void deliver(UUID notificationId, String recipientEmail, String subject, String body) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if (attemptSend(notificationId, recipientEmail, subject, body)) {
                recordSent(notificationId);
            }
        } finally {
            sample.stop(meterRegistry.timer("notification.delivery.duration", "channel", "EMAIL"));
        }
    }
    private boolean attemptSend(UUID notificationId, String recipientEmail, String subject, String body) {
        try {
            retryTemplate.invoke(() -> mailSender.send(toMailMessage(recipientEmail, subject, body)));
            return true;
        } catch (MailException e) {
            recordFailure(notificationId, classify(e), e);
            return false;
        } catch (Exception e) {
            recordFailure(notificationId, FailureReason.UNEXPECTED_FAILURE, e);
            return false;
        }
    }
    private static SimpleMailMessage toMailMessage(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        return message;
    }
    private static FailureReason classify(MailException e) {
        return switch (e) {
            case MailAuthenticationException ex -> FailureReason.CONFIGURATION_FAILURE;
            case MailParseException ex -> FailureReason.INVALID_RECIPIENT;
            case MailPreparationException ex -> FailureReason.CONFIGURATION_FAILURE;
            case MailSendException ex -> FailureReason.TRANSIENT_PROVIDER_FAILURE;
            default -> FailureReason.UNEXPECTED_FAILURE;
        };
    }
    private void recordFailure(UUID notificationId, FailureReason reason, Exception e) {
        markFailed(notificationId, reason);
        meterRegistry.counter("notification.failed", "channel", "EMAIL", "reason", reason.name()).increment();
        if (reason == FailureReason.UNEXPECTED_FAILURE) {
            log.error("Notification {} delivery failed unexpectedly", notificationId, e);
        } else {
            log.warn("Notification {} delivery failed: {} ({})", notificationId, reason, e.getClass().getSimpleName());
        }
    }
    private void recordSent(UUID notificationId) {
        try {
            markSent(notificationId);
            meterRegistry.counter("notification.sent", "channel", "EMAIL").increment();
        } catch (Exception e) {
            log.error("Notification {} was delivered but recording it as SENT failed", notificationId, e);
        }
    }
    void markSent(UUID notificationId) {
        repository.findById(notificationId).ifPresent(n -> {
            n.markSent(clock.instant());
            repository.save(n);
        });
    }
    void markFailed(UUID notificationId, FailureReason reason) {
        repository.findById(notificationId).ifPresent(n -> {
            n.markFailed(reason);
            repository.save(n);
        });
    }
}
