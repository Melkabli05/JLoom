package {{package}}.notification.application.service;
import {{package}}.notification.infrastructure.configuration.NotificationRecoveryProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
class PendingNotificationRecoveryTask {
    private final NotificationService notificationService;
    private final NotificationRecoveryProperties properties;
    PendingNotificationRecoveryTask(NotificationService notificationService, NotificationRecoveryProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }
    @Scheduled(fixedDelayString = "${notification.recovery.interval:PT1M}")
    void run() {
        notificationService.redispatchStuckPending(properties.staleAfter());
    }
}
