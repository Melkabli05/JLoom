package {{package}}.file.application.service;
import {{package}}.file.infrastructure.configuration.MediaProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
class MediaCleanupTaskTest {
    @Test
    void runDelegatesToMediaService() {
        MediaService service = mock(MediaService.class);
        when(service.purgeExpired(any(Instant.class), anyInt())).thenReturn(3);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MediaProperties properties = new MediaProperties(null,
                new MediaProperties.Cleanup(Duration.ofMinutes(15), 200),
                "ROLE_ADMIN");
        MediaCleanupTask task = new MediaCleanupTask(service, meterRegistry,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                properties);
        task.run();
        assertThat(meterRegistry.counter("media.cleanup.deleted").count()).isEqualTo(3.0);
    }
    @Test
    void runSkipsMeterWhenNothingDeleted() {
        MediaService service = mock(MediaService.class);
        when(service.purgeExpired(any(Instant.class), anyInt())).thenReturn(0);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MediaProperties properties = new MediaProperties(null,
                new MediaProperties.Cleanup(Duration.ofMinutes(15), 200),
                "ROLE_ADMIN");
        MediaCleanupTask task = new MediaCleanupTask(service, meterRegistry, Clock.systemUTC(), properties);
        task.run();
        assertThat(meterRegistry.counter("media.cleanup.deleted").count()).isEqualTo(0.0);
    }
}