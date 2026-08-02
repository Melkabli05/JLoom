package {{package}}.file.application.service;

import {{package}}.file.infrastructure.configuration.MediaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class MediaCleanupTask {

    private final MediaService mediaService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final MediaProperties.Cleanup cleanup;

    public MediaCleanupTask(MediaService mediaService, MeterRegistry meterRegistry,
                            Clock clock, MediaProperties mediaProperties) {
        this.mediaService = mediaService;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.cleanup = mediaProperties.cleanup();
    }

    @Scheduled(fixedDelayString = "${media.cleanup.interval:PT15M}")
    public void run() {
        int deleted = mediaService.purgeExpired(clock.instant(), cleanup.batchSize());
        if (deleted > 0) {
            meterRegistry.counter("media.cleanup.deleted").increment(deleted);
        }
    }
}