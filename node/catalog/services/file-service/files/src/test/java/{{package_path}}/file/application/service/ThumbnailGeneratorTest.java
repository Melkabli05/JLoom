package {{package}}.file.application.service;

import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaAssetStatus;
import {{package}}.file.domain.model.MediaVisibility;
import {{package}}.file.infrastructure.configuration.MediaProperties;
import {{package}}.file.infrastructure.persistence.MediaAssetRepository;
import {{package}}.file.infrastructure.storage.LocalFilesystemStorage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThumbnailGeneratorTest {

    @TempDir
    Path tempDir;

    private MediaAssetRepository repository;
    private LocalFilesystemStorage storage;
    private ThumbnailGenerator generator;

    @BeforeEach
    void setUp() {
        repository = mock(MediaAssetRepository.class);
        when(repository.save(any(MediaAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        storage = new LocalFilesystemStorage(tempDir);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        RetryPolicy noRetry = RetryPolicy.builder().maxRetries(0).build();
        generator = new ThumbnailGenerator(repository, storage,
                new MediaProperties(null, null, "ROLE_ADMIN"),
                new RetryTemplate(noRetry), meterRegistry);
    }

    @Test
    void generatePopulatesThumbnailKeyForPng() throws Exception {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Instant now = Instant.now();
        byte[] realPng = makeRealPng(32, 32);
        MediaAsset asset = new MediaAsset(id, "test.png", "image/png", realPng.length, "",
                owner, "USER_AVATAR", MediaVisibility.PRIVATE,
                MediaAssetStatus.AVAILABLE, "test.png", now, now, null, null);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(asset));
        storage.put(asset.getStorageKey(), new ByteArrayInputStream(realPng), realPng.length, "image/png");

        generator.generate(id);

        MediaAsset after = repository.findById(id).orElseThrow();
        assertThat(after.getThumbnailKey()).isNotBlank();
    }

    private byte[] makeRealPng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}