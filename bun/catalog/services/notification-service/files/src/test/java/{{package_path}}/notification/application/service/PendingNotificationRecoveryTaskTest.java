package {{package}}.notification.application.service;

import {{package}}.notification.infrastructure.configuration.NotificationRecoveryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Duration;
import static org.mockito.Mockito.verify;
@ExtendWith(MockitoExtension.class)
class PendingNotificationRecoveryTaskTest {
    @Mock
    private NotificationService notificationService;
    @Test
    void runRedispatchesUsingTheConfiguredStaleAfterThreshold() {
        NotificationRecoveryProperties properties = new NotificationRecoveryProperties(Duration.ofMinutes(5));
        PendingNotificationRecoveryTask task = new PendingNotificationRecoveryTask(notificationService, properties);
        task.run();
        verify(notificationService).redispatchStuckPending(Duration.ofMinutes(5));
    }
}
