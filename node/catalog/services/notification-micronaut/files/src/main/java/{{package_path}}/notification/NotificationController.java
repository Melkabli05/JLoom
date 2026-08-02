package {{package}}.notification;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;

import java.util.List;
import java.util.UUID;

@Controller("/notifications")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @Post
    @Produces(MediaType.APPLICATION_JSON)
    NotificationResponse send(@Body NotificationRequest request) {
        UUID id = service.send(new NotificationRecord(
                UUID.randomUUID(), request.channel(), request.recipient(), request.message(), null));
        return new NotificationResponse(id);
    }

    @Get
    @Produces(MediaType.APPLICATION_JSON)
    List<NotificationRecord> list() {
        return service.sent();
    }

    public record NotificationRequest(String channel, String recipient, String message) {
    }

    public record NotificationResponse(UUID id) {
    }
}