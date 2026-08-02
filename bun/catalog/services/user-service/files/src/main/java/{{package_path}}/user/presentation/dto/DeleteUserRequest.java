package {{package}}.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;
public record DeleteUserRequest(
        @NotBlank String currentPassword
) {
    @Override
    public String toString() {
        return "DeleteUserRequest[currentPassword=REDACTED]";
    }
}
