package {{package}}.file.application.service;

import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaPurpose;
import {{package}}.file.domain.model.MediaVisibility;
import {{package}}.file.infrastructure.storage.MediaObject;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
public interface MediaService {
    MediaAsset upload(UUID ownerId, MediaPurpose purpose, MediaVisibility visibility,
                      String originalFilename, InputStream content, long contentLength,
                      String claimedContentType, String idempotencyKey);
    Optional<MediaAsset> find(UUID id);
    MediaObject download(UUID id);
    URI presignPut(UUID ownerId, MediaPurpose purpose, MediaVisibility visibility,
                   String contentType, String originalFilename);
    int purgeExpired(Instant cutoff, int batchSize);
}
