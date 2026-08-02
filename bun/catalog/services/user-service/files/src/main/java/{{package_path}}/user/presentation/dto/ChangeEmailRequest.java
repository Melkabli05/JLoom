package {{package}}.user.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 255) String newEmail,
        @NotBlank String currentPassword
) {
}
