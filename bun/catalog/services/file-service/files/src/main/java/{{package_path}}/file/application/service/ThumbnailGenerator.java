package {{package}}.file.application.service;

import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.infrastructure.configuration.MediaProperties;
import {{package}}.file.infrastructure.persistence.MediaAssetRepository;
import {{package}}.file.infrastructure.storage.MediaKeyFactory;
import {{package}}.file.infrastructure.storage.MediaObject;
import {{package}}.file.infrastructure.storage.MediaStorage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
@Component
public class ThumbnailGenerator {
    private static final Logger log = LoggerFactory.getLogger(ThumbnailGenerator.class);
    private final MediaAssetRepository repository;
    private final MediaStorage storage;
    private final MediaProperties mediaProperties;
    private final RetryTemplate retryTemplate;
    private final MeterRegistry meterRegistry;
    public ThumbnailGenerator(MediaAssetRepository repository, MediaStorage storage,
                              MediaProperties mediaProperties, RetryTemplate thumbnailGenerationRetryTemplate,
                              MeterRegistry meterRegistry) {
        this.repository = repository;
        this.storage = storage;
        this.mediaProperties = mediaProperties;
        this.retryTemplate = thumbnailGenerationRetryTemplate;
        this.meterRegistry = meterRegistry;
    }
    @Async
    @Transactional
    public void generate(UUID mediaAssetId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            retryTemplate.invoke(() -> {
                try {
                    doGenerate(mediaAssetId);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return null;
            });
            meterRegistry.counter("media.thumbnail.generated").increment();
        } catch (Exception e) {
            meterRegistry.counter("media.thumbnail.failed").increment();
            log.warn("Thumbnail generation failed for {}: {}", mediaAssetId, e.getMessage());
        } finally {
            sample.stop(meterRegistry.timer("media.thumbnail.duration"));
        }
    }
    private void doGenerate(UUID mediaAssetId) throws IOException {
        MediaAsset asset = repository.findById(mediaAssetId).orElse(null);
        if (asset == null) return;
        MediaObject original = storage.get(asset.getStorageKey());
        try (InputStream in = original.content()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return;
            }
            StringBuilder composite = new StringBuilder();
            for (String size : mediaProperties.thumbnails().sizes()) {
                int separator = size.indexOf('x');
                int w = Integer.parseInt(size.substring(0, separator));
                int h = Integer.parseInt(size.substring(separator + 1));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Thumbnails.of(image)
                        .size(w, h)
                        .outputFormat("jpg")
                        .outputQuality(mediaProperties.thumbnails().jpegQuality() / 100.0)
                        .toOutputStream(baos);
                byte[] bytes = baos.toByteArray();
                String thumbKey = MediaKeyFactory.forThumbnail(asset.getStorageKey(), w, h);
                storage.put(thumbKey, new ByteArrayInputStream(bytes), bytes.length, "image/jpeg");
                if (composite.length() > 0) composite.append(',');
                composite.append(w).append('x').append(h).append(':').append(thumbKey);
            }
            asset.setThumbnailKey(composite.toString());
            repository.save(asset);
        }
    }
}
