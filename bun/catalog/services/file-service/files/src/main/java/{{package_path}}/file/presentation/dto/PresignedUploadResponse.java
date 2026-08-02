package {{package}}.file.presentation.dto;

import java.net.URI;
import java.time.Instant;
public record PresignedUploadResponse(
        URI url,
        String key,
        Instant expiresAt
) {
}
