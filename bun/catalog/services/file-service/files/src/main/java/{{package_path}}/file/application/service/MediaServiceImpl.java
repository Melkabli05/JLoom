package {{package}}.file.application.service;

import {{package}}.file.application.exception.PayloadTooLargeException;
import {{package}}.file.application.exception.PresignUnsupportedException;
import {{package}}.file.application.exception.UnsupportedMediaTypeException;
import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaAssetStatus;
import {{package}}.file.domain.model.MediaPurpose;
import {{package}}.file.domain.model.MediaVisibility;
import {{package}}.file.infrastructure.configuration.MediaProperties;
import {{package}}.file.infrastructure.configuration.MediaStorageProperties;
import {{package}}.file.infrastructure.persistence.MediaAssetRepository;
import {{package}}.file.infrastructure.storage.MediaKeyFactory;
import {{package}}.file.infrastructure.storage.MediaNotFoundException;
import {{package}}.file.infrastructure.storage.MediaObject;
import {{package}}.file.infrastructure.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
@Service
class MediaServiceImpl implements MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaServiceImpl.class);
    private static final int PEEK_SIZE = 16;
    private final MediaAssetRepository repository;
    private final MediaStorage storage;
    private final MediaValidator validator;
    private final ThumbnailGenerator thumbnailGenerator;
    private final MediaStorageProperties storageProperties;
    private final Clock clock;
    MediaServiceImpl(MediaAssetRepository repository, MediaStorage storage,
                     MediaValidator validator, ThumbnailGenerator thumbnailGenerator,
                     MediaStorageProperties storageProperties, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
        this.thumbnailGenerator = thumbnailGenerator;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }
    @Override
    @Transactional
    public MediaAsset upload(UUID ownerId, MediaPurpose purpose, MediaVisibility visibility,
                             String originalFilename, InputStream content, long contentLength,
                             String claimedContentType, String idempotencyKey) {
        if (idempotencyKey != null && ownerId != null) {
            Optional<MediaAsset> existing = repository
                    .findFirstByOwnerIdAndPurposeAndIdempotencyKeyAndStatusNot(
                            ownerId, purpose.name(), idempotencyKey, MediaAssetStatus.FAILED);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        long maxBytes = storageProperties.maxFileSize().toBytes();
        byte[] peek = new byte[PEEK_SIZE];
        int peekRead = 0;
        try {
            while (peekRead < PEEK_SIZE) {
                int n = content.read(peek, peekRead, PEEK_SIZE - peekRead);
                if (n < 0) break;
                peekRead += n;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload body for sniffing", e);
        }
        String sniffedContentType = validator.matchSniffed(peek, peekRead);
        if (sniffedContentType == null || !validator.allowedContentTypes().contains(sniffedContentType)) {
            throw new UnsupportedMediaTypeException(
                    claimedContentType != null ? claimedContentType : "unknown",
                    validator.allowedContentTypes());
        }
        byte[] prefix = new byte[peekRead];
        System.arraycopy(peek, 0, prefix, 0, peekRead);
        InputStream bounded = new SizeLimitedInputStream(
                new SequenceInputStream(new ByteArrayInputStream(prefix), content), maxBytes);
        String key = MediaKeyFactory.forUpload(purpose, originalFilename);
        Instant now = clock.instant();
        MediaAsset asset = new MediaAsset(
                UUID.randomUUID(), key, sniffedContentType, 0L, "",
                ownerId, purpose.name(), visibility,
                MediaAssetStatus.PENDING, originalFilename, now, null, null, null);
        try {
            storage.putAsync(key, bounded, contentLength, sniffedContentType).join();
            asset.setStatus(MediaAssetStatus.AVAILABLE);
            asset.setAvailableAt(now);
        } catch (RuntimeException e) {
            log.warn("Storage put failed for key {}: {}", key, e.getMessage());
            asset.setStatus(MediaAssetStatus.FAILED);
            try {
                repository.save(asset);
            } catch (RuntimeException ignored) {
            }
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
        MediaAsset saved;
        try {
            saved = repository.save(asset);
        } catch (DataIntegrityViolationException e) {
            log.warn("Idempotency-key collision detected");
            Optional<MediaAsset> existing = repository
                    .findFirstByOwnerIdAndPurposeAndIdempotencyKeyAndStatusNot(
                            ownerId, purpose.name(), idempotencyKey, MediaAssetStatus.FAILED);
            if (existing.isPresent()) {
                try { storage.delete(key); } catch (RuntimeException ignored) { }
                return existing.get();
            }
            throw e;
        }
        if (isImage(saved.getContentType())) {
            thumbnailGenerator.generate(saved.getId());
        }
        return saved;
    }
    @Override
    @Transactional(readOnly = true)
    public Optional<MediaAsset> find(UUID id) {
        return repository.findById(id);
    }
    @Override
    @Transactional(readOnly = true)
    public MediaObject download(UUID id) {
        MediaAsset asset = repository.findById(id)
                .orElseThrow(() -> new MediaNotFoundException(id.toString()));
        MediaObject object = storage.get(asset.getStorageKey());
        return new MediaObject(object.content(), object.contentType(),
                object.sizeBytes(), asset.getChecksum());
    }
    @Override
    public URI presignPut(UUID ownerId, MediaPurpose purpose, MediaVisibility visibility,
                          String contentType, String originalFilename) {
        String key = MediaKeyFactory.forUpload(purpose, originalFilename);
        try {
            return storage.presignedPutUrl(key, storageProperties.minio().presignTtl(), contentType);
        } catch (UnsupportedOperationException e) {
            throw new PresignUnsupportedException("Presigned URLs require MinIO storage");
        }
    }
    @Override
    @Transactional
    public int purgeExpired(Instant cutoff, int batchSize) {
        var expired = repository.findByExpiresAtBeforeAndStatusNot(
                cutoff, MediaAssetStatus.DELETED,
                PageRequest.of(0, Math.max(1, batchSize)));
        int deleted = 0;
        for (MediaAsset asset : expired) {
            try {
                try { storage.delete(asset.getStorageKey()); } catch (RuntimeException ignored) { }
                if (asset.getThumbnailKey() != null) {
                    try { storage.delete(asset.getThumbnailKey()); } catch (RuntimeException ignored) { }
                }
                repository.delete(asset);
                deleted++;
            } catch (RuntimeException e) {
                log.warn("Failed to purge media asset {}: {}", asset.getId(), e.getMessage());
            }
        }
        return deleted;
    }
    private static boolean isImage(String contentType) {
        return contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/png")
                || contentType.equals("image/webp") || contentType.equals("image/gif"));
    }
    private static final class SizeLimitedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long count;
        SizeLimitedInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }
        private void check() {
            if (count > maxBytes) {
                throw new PayloadTooLargeException("Upload exceeds " + maxBytes + " bytes");
            }
        }
        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) { count++; check(); }
            return b;
        }
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) { count += n; check(); }
            return n;
        }
    }
}
