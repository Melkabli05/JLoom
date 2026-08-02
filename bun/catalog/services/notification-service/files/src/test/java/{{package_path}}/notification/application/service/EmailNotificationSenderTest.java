package {{package}}.notification.application.service;

import {{package}}.notification.domain.model.FailureReason;
import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.domain.model.NotificationStatus;
import {{package}}.notification.infrastructure.persistence.NotificationRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationSenderTest {

    private static final String SENSITIVE_BODY = "your OTP is 123456";

    @Mock
    private MailSender mailSender;

    @Mock
    private NotificationRepository repository;

    private EmailNotificationSender sender;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .includes(MailSendException.class)
                .maxRetries(3)
                .delay(Duration.ofMillis(1))
                .multiplier(1.0)
                .build());
        sender = new EmailNotificationSender(mailSender, repository, fixed, new SimpleMeterRegistry(), retryTemplate);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(EmailNotificationSender.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(EmailNotificationSender.class)).detachAppender(logAppender);
    }

    @Test
    void successfulSendMarksNotificationSent() {
        UUID id = UUID.randomUUID();
        Notification notification = pendingNotification(id);
        when(repository.findById(id)).thenReturn(Optional.of(notification));

        sender.deliver(id, "alice@example.com", "hello", SENSITIVE_BODY);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(NotificationStatus.SENT, notification.getStatus());
    }

    @Test
    void nonRetryableFailureFailsImmediatelyWithoutRetrying() {
        UUID id = UUID.randomUUID();
        Notification notification = pendingNotification(id);
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        doThrow(new MailAuthenticationException("bad credentials")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.deliver(id, "alice@example.com", "hello", SENSITIVE_BODY);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals(FailureReason.CONFIGURATION_FAILURE, notification.getLastError());
    }

    @Test
    void invalidRecipientFailsImmediatelyWithoutRetrying() {
        UUID id = UUID.randomUUID();
        Notification notification = pendingNotification(id);
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        doThrow(new MailParseException("illegal recipient address")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.deliver(id, "alice@example.com", "hello", SENSITIVE_BODY);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals(FailureReason.INVALID_RECIPIENT, notification.getLastError());
    }

    @Test
    void transientFailureIsRetriedUpToTheBoundThenFails() {
        UUID id = UUID.randomUUID();
        Notification notification = pendingNotification(id);
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        doThrow(new MailSendException("smtp timeout")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.deliver(id, "alice@example.com", "hello", SENSITIVE_BODY);

        verify(mailSender, times(4)).send(any(SimpleMailMessage.class));
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals(FailureReason.TRANSIENT_PROVIDER_FAILURE, notification.getLastError());
    }

    @Test
    void unexpectedNonMailExceptionStillReachesAFailedTerminalStateWithoutRetrying() {
        UUID id = UUID.randomUUID();
        Notification notification = pendingNotification(id);
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        doThrow(new IllegalStateException("bug, not a provider failure")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.deliver(id, "alice@example.com", "hello", SENSITIVE_BODY);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals(FailureReason.UNEXPECTED_FAILURE, notification.getLastError());
    }

    @Test
    void deliveryConfirmedSentEvenWhenRecordingTheResultFails() {
        UUID id = UUID.randomUUID();
        Notification notification = pendingNotification(id);
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        when(repository.save(notification)).thenThrow(new RuntimeException("db write failed"));

        sender.deliver(id, "alice@example.com", "hello", SENSITIVE_BODY);

        assertEquals(NotificationStatus.SENT, notification.getStatus(),
                "the email was actually sent — a bookkeeping failure must not be recorded as a delivery failure");
        assertNull(notification.getLastError());
    }

    @Test
    void deliveryNeverLogsSensitiveNotificationContent() {
        UUID id = UUID.randomUUID();
        Notification notification = pendingNotification(id);
        when(repository.findById(id)).thenReturn(Optional.of(notification));
        doThrow(new MailSendException("smtp timeout")).when(mailSender).send(any(SimpleMailMessage.class));

        sender.deliver(id, "alice@example.com", "hello", SENSITIVE_BODY);

        boolean leaked = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains(SENSITIVE_BODY));
        assertFalse(leaked, "log output must never contain the notification body");
    }

    private static Notification pendingNotification(UUID id) {
        return new Notification(id, "alice@example.com", "hello", SENSITIVE_BODY,
                NotificationChannel.EMAIL, NotificationStatus.PENDING, null, 0, null, Instant.now(), null);
    }
}
