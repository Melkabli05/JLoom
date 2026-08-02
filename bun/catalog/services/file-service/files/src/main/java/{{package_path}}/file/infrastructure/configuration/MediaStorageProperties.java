package {{package}}.file.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import java.time.Duration;
@ConfigurationProperties(prefix = "media.storage")
public record MediaStorageProperties(
        String type,
        Local local,
        Minio minio,
        DataSize maxFileSize
) {
    public MediaStorageProperties {
        if (type == null || type.isBlank()) {
            type = "local";
        }
        if (maxFileSize == null) {
            maxFileSize = DataSize.ofMegabytes(100);
        }
    }
    public record Local(String baseDir) {
        public Local {
            if (baseDir == null || baseDir.isBlank()) {
                baseDir = "/tmp/jloom-media";
            }
        }
    }
    public record Minio(
            String endpoint,
            String accessKey,
            String secretKey,
            String region,
            String bucket,
            Duration presignTtl
    ) {
        public Minio {
            if (presignTtl == null) {
                presignTtl = Duration.ofMinutes(15);
            }
            if (region == null || region.isBlank()) {
                region = "us-east-1";
            }
        }
    }
}
