package {{package}}.file.presentation.dto;

import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaAssetStatus;
import {{package}}.file.domain.model.MediaVisibility;
import java.time.Instant;
import java.util.UUID;
public record MediaView(
        UUID id,
        String key,
        String contentType,
        long sizeBytes,
        String checksum,
        UUID ownerId,
        String purpose,
        MediaVisibility visibility,
        MediaAssetStatus status,
        String originalFilename,
        Instant createdAt,
        Instant availableAt,
        Instant expiresAt
) {
    public static MediaView from(MediaAsset asset) {
        return new MediaView(
                asset.getId(),
                asset.getStorageKey(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getChecksum(),
                asset.getOwnerId(),
                asset.getPurpose(),
                asset.getVisibility(),
                asset.getStatus(),
                asset.getOriginalFilename(),
                asset.getCreatedAt(),
                asset.getAvailableAt(),
                asset.getExpiresAt());
    }
}
