package {{package}}.user.presentation.dto;

import {{package}}.user.domain.model.Role;
import java.util.UUID;
public record VerifyCredentialsResponse(UUID id, Role role) {
}
