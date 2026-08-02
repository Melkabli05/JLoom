package {{package}}.notification.application.service;
import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.domain.model.NotificationChannel;
import {{package}}.notification.domain.model.NotificationStatus;
import {{package}}.notification.infrastructure.persistence.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository repository;
    private final Map<NotificationChannel, NotificationSender> sendersByChannel;
    private final Clock clock;
    NotificationServiceImpl(NotificationRepository repository, List<NotificationSender> senders, Clock clock) {
        this.repository = repository;
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toMap(NotificationSender::channel, Function.identity()));
        this.clock = clock;
    }
    @Override
    public Notification submit(String recipientEmail, String subject, String body, String idempotencyKey) {
        boolean hasIdempotencyKey = StringUtils.hasText(idempotencyKey);
        if (hasIdempotencyKey) {
            var existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        NotificationChannel channel = NotificationChannel.EMAIL;
        Notification notification = new Notification(
                UUID.randomUUID(), recipientEmail, subject, body, channel,
                NotificationStatus.PENDING, idempotencyKey, 0, null, clock.instant(), null);
        Notification saved;
        try {
            saved = repository.save(notification);
        } catch (DataIntegrityViolationException e) {
            if (!hasIdempotencyKey) {
                throw e;
            }
            saved = repository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
            return saved;
        }
        senderFor(channel).deliver(saved.getId(), saved.getRecipientEmail(), saved.getSubject(), saved.getBody());
        return saved;
    }
    @Override
    public Notification getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "notification not found: " + id));
    }
    @Override
    public Page<Notification> list(Pageable pageable) {
        return repository.findAll(pageable);
    }
    @Override
    public void redispatchStuckPending(Duration staleAfter) {
        var cutoff = clock.instant().minus(staleAfter);
        for (Notification notification : repository.findByStatusAndCreatedAtBefore(NotificationStatus.PENDING, cutoff)) {
            senderFor(notification.getChannel()).deliver(
                    notification.getId(), notification.getRecipientEmail(), notification.getSubject(), notification.getBody());
        }
    }
    private NotificationSender senderFor(NotificationChannel channel) {
        NotificationSender sender = sendersByChannel.get(channel);
        if (sender == null) {
            throw new IllegalStateException("no NotificationSender registered for channel " + channel);
        }
        return sender;
    }
}
