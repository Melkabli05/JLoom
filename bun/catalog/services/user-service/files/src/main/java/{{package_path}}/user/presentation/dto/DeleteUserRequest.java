package {{package}}.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;
public record DeleteUserRequest(
        @NotBlank String currentPassword
) {
}
