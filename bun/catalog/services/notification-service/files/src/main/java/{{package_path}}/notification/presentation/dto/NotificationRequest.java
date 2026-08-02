package {{package}}.notification.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record NotificationRequest(
        @NotBlank @Email @Size(max = 255) String recipientEmail,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 10000) String body
) {
}
