package {{package}}.notification.presentation.controller;

import {{package}}.notification.application.service.NotificationService;
import {{package}}.notification.domain.model.Notification;
import {{package}}.notification.presentation.dto.NotificationRequest;
import {{package}}.notification.presentation.dto.NotificationResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
class InternalNotificationController {

    private final NotificationService service;
    private final String serviceKey;

    InternalNotificationController(NotificationService service, @Value("${internal.service-key:}") String serviceKey) {
        this.service = service;
        this.serviceKey = serviceKey;
    }

    @PostMapping
    ResponseEntity<NotificationResponse> submit(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String providedKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NotificationRequest request) {
        if (serviceKey.isBlank() || !serviceKey.equals(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Notification notification = service.submit(request.recipientEmail(), request.subject(), request.body(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new NotificationResponse(notification.getId(), notification.getStatus()));
    }
}
