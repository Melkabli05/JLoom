package {{package}}.user.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RegistrationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
    // Never let this fall back to the default record toString() — it would print the plaintext
    // password straight into the application log wherever this request body gets logged (e.g.
    // aop's LoggingAspect, which logs @RestController method args at DEBUG).
    @Override
    public String toString() {
        return "RegistrationRequest[email=" + email + ", password=REDACTED]";
    }
}
