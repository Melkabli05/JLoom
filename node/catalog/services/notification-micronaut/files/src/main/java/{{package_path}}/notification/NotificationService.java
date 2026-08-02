package {{package}}.notification;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    UUID send(NotificationRecord record);

    List<NotificationRecord> sent();
}