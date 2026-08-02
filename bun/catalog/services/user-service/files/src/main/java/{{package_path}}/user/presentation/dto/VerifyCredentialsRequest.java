package {{package}}.user.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record VerifyCredentialsRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
    @Override
    public String toString() {
        return "VerifyCredentialsRequest[email=" + email + ", password=REDACTED]";
    }
}
