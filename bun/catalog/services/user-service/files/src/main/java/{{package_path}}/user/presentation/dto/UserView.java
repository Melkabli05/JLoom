package {{package}}.user.presentation.dto;

import {{package}}.user.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

public record UserView(UUID id, String email, Instant createdAt, Instant emailVerifiedAt, Role role, boolean enabled) {
}
