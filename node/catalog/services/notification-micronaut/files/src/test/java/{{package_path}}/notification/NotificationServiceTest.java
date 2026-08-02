package {{package}}.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationServiceTest {

    @Test
    void canCreateAndReadRecords() {
        UUID id = UUID.randomUUID();
        NotificationRecord record = new NotificationRecord(id, "email", "x@y.com", "hi", Instant.now());

        assertNotNull(record.id());
        assertEquals("email", record.channel());
        assertEquals("x@y.com", record.recipient());
        assertEquals("hi", record.message());
    }
}