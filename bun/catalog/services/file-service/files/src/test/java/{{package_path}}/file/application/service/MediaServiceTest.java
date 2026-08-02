package {{package}}.file.application.service;

import {{package}}.file.application.exception.UnsupportedMediaTypeException;
import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaAssetStatus;
import {{package}}.file.domain.model.MediaPurpose;
import {{package}}.file.domain.model.MediaVisibility;
import {{package}}.file.infrastructure.configuration.MediaProperties;
import {{package}}.file.infrastructure.configuration.MediaStorageProperties;
import {{package}}.file.infrastructure.persistence.MediaAssetRepository;
import {{package}}.file.infrastructure.storage.LocalFilesystemStorage;
import {{package}}.file.infrastructure.storage.MediaNotFoundException;
import {{package}}.file.infrastructure.storage.MediaObject;
import {{package}}.file.infrastructure.storage.MediaStorage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.unit.DataSize;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
class MediaServiceTest {
    @TempDir
    Path tempDir;
    private MediaStorage storage;
    private MediaAssetRepository repository;
    private MediaServiceImpl service;
    private ThumbnailGenerator thumbnailGenerator;
    @BeforeEach
    void setUp() {
        storage = new LocalFilesystemStorage(tempDir);
        repository = mock(MediaAssetRepository.class);
        when(repository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        thumbnailGenerator = noOpThumbnailGenerator();
        MediaStorageProperties storageProperties = new MediaStorageProperties(
                "local",
                new MediaStorageProperties.Local(tempDir.toString()),
                new MediaStorageProperties.Minio("http://localhost:9000", "minioadmin", "minioadmin",
                        "us-east-1", "bucket", Duration.ofMinutes(15)),
                DataSize.ofMegabytes(10));
        MediaProperties mediaProperties = new MediaProperties(null, null, "ROLE_ADMIN");
        service = new MediaServiceImpl(repository, storage, new MediaValidator(),
                thumbnailGenerator, storageProperties,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }
    @Test
    void uploadPersistsAndReturnsAsset() {
        when(repository.findFirstByOwnerIdAndPurposeAndIdempotencyKeyAndStatusNot(any(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        byte[] body = pngBytes();
        MediaAsset asset = service.upload(UUID.randomUUID(), MediaPurpose.USER_AVATAR,
                MediaVisibility.PRIVATE, "avatar.png", new ByteArrayInputStream(body),
                body.length, "image/png", null);
        assertThat(asset.getStatus()).isEqualTo(MediaAssetStatus.AVAILABLE);
        assertThat(asset.getContentType()).isEqualTo("image/png");
    }
    @Test
    void uploadRejectsUnsupportedContentType() {
        byte[] body = new byte[]{0x00, 0x01, 0x02, 0x03};
        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), MediaPurpose.ATTACHMENT,
                MediaVisibility.PRIVATE, "x.bin", new ByteArrayInputStream(body),
                body.length, "application/x-unknown", null))
                .isInstanceOf(UnsupportedMediaTypeException.class);
    }
    @Test
    void findReturnsEmptyForUnknownId() {
        when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThat(service.find(UUID.randomUUID())).isEmpty();
    }
    @Test
    void downloadThrowsMediaNotFoundForUnknownId() {
        when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download(UUID.randomUUID()))
                .isInstanceOf(MediaNotFoundException.class);
    }
    @Test
    void purgeExpiredDelegatesToRepository() {
        when(repository.findByExpiresAtBeforeAndStatusNot(any(), any(), any()))
                .thenReturn(java.util.List.of());
        int deleted = service.purgeExpired(Instant.parse("2026-06-01T00:00:00Z"), 100);
        assertThat(deleted).isZero();
    }
    private ThumbnailGenerator noOpThumbnailGenerator() {
        RetryPolicy noRetry = RetryPolicy.builder().maxRetries(0).build();
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        return new ThumbnailGenerator(repository, storage, new MediaProperties(null, null, "ROLE_ADMIN"),
                new RetryTemplate(noRetry), meterRegistry) {
            @Override
            public void generate(UUID id) {
            }
        };
    }
    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }
}
