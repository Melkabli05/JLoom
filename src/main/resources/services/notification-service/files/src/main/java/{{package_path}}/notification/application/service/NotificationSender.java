package {{package}}.notification.application.service;

import {{package}}.notification.domain.model.NotificationChannel;

import java.util.UUID;

public interface NotificationSender {

    NotificationChannel channel();

    void deliver(UUID notificationId, String recipientEmail, String subject, String body);
}
