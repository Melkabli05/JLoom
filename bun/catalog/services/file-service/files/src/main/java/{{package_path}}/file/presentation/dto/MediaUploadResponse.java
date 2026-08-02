package {{package}}.file.presentation.dto;
import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaAssetStatus;
import java.util.UUID;
public record MediaUploadResponse(
        UUID id,
        String key,
        MediaAssetStatus status,
        String contentType,
        long sizeBytes
) {
    public static MediaUploadResponse from(MediaAsset asset) {
        return new MediaUploadResponse(
                asset.getId(),
                asset.getStorageKey(),
                asset.getStatus(),
                asset.getContentType(),
                asset.getSizeBytes());
    }
}