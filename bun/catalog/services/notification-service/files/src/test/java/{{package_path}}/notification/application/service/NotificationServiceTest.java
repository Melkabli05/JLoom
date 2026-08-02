package {{package}}.notification.application.service;

import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.domain.model.NotificationStatus;
import {{package}}.notification.infrastructure.persistence.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationSender sender;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(sender.channel()).thenReturn(NotificationChannel.EMAIL);
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        service = new NotificationServiceImpl(repository, List.of(sender), fixed);
    }

    @Test
    void submitPersistsPendingAndDispatchesDelivery() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Notification result = service.submit("alice@example.com", "hello", "world", null);

        assertEquals(NotificationStatus.PENDING, result.getStatus());
        assertEquals(NotificationChannel.EMAIL, result.getChannel());
        verify(sender).deliver(result.getId(), "alice@example.com", "hello", "world");
    }

    @Test
    void submitWithExistingIdempotencyKeyReturnsExistingNotificationWithoutDispatching() {
        Notification existing = new Notification(UUID.randomUUID(), "alice@example.com", "hello", "world",
                NotificationChannel.EMAIL, NotificationStatus.SENT, "key-1", 1, null, Instant.now(), Instant.now());
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Notification result = service.submit("alice@example.com", "hello", "world", "key-1");

        assertSame(existing, result);
        verify(repository, never()).save(any());
        verify(sender, never()).deliver(any(), any(), any(), any());
    }

    @Test
    void submitRacingOnIdempotencyKeyReturnsTheWinnerInsteadOfDuplicating() {
        Notification winner = new Notification(UUID.randomUUID(), "alice@example.com", "hello", "world",
                NotificationChannel.EMAIL, NotificationStatus.PENDING, "key-1", 0, null, Instant.now(), null);
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty(), Optional.of(winner));

        Notification result = service.submit("alice@example.com", "hello", "world", "key-1");

        assertSame(winner, result);
        verify(sender, never()).deliver(any(), any(), any(), any());
    }

    @Test
    void submitWithoutIdempotencyKeyAlwaysCreatesANewNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit("bob@example.com", "subject", "body", null);

        verify(repository, never()).findByIdempotencyKey(any());
        assertEquals("bob@example.com", captor.getValue().getRecipientEmail());
    }

    @Test
    void redispatchStuckPendingRedeliversEachStaleNotificationOnItsOwnChannel() {
        Notification stuck = new Notification(UUID.randomUUID(), "carol@example.com", "subject", "body",
                NotificationChannel.EMAIL, NotificationStatus.PENDING, null, 0, null, Instant.now(), null);
        when(repository.findByStatusAndCreatedAtBefore(eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stuck));

        service.redispatchStuckPending(Duration.ofMinutes(2));

        verify(sender).deliver(stuck.getId(), "carol@example.com", "subject", "body");
    }

    @Test
    void redispatchStuckPendingDoesNothingWhenNoneAreStale() {
        when(repository.findByStatusAndCreatedAtBefore(eq(NotificationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        service.redispatchStuckPending(Duration.ofMinutes(2));

        verify(sender, never()).deliver(any(), any(), any(), any());
    }
}
