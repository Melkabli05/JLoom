package {{package}}.file.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;
@ConfigurationProperties(prefix = "media")
public record MediaProperties(
        Thumbnails thumbnails,
        Cleanup cleanup,
        String adminAuthority
) {
    public MediaProperties {
        if (thumbnails == null) {
            thumbnails = new Thumbnails(List.of("64x64", "256x256"), 85);
        }
        if (cleanup == null) {
            cleanup = new Cleanup(Duration.ofMinutes(15), 200);
        }
        if (adminAuthority == null || adminAuthority.isBlank()) {
            adminAuthority = "ROLE_ADMIN";
        }
    }
    public record Thumbnails(List<String> sizes, int jpegQuality) {
        public Thumbnails {
            if (sizes == null || sizes.isEmpty()) {
                sizes = List.of("64x64", "256x256");
            }
            if (jpegQuality <= 0 || jpegQuality > 100) {
                jpegQuality = 85;
            }
        }
    }
    public record Cleanup(Duration interval, int batchSize) {
        public Cleanup {
            if (interval == null) {
                interval = Duration.ofMinutes(15);
            }
            if (batchSize <= 0) {
                batchSize = 200;
            }
        }
    }
}
