package {{package}}.user;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String passwordHash,
        Instant createdAt,
        Instant emailVerifiedAt
) {
}