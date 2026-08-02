package {{package}}.file.infrastructure.configuration;
import {{package}}.file.infrastructure.storage.LocalFilesystemStorage;
import {{package}}.file.infrastructure.storage.MediaStorage;
import {{package}}.file.infrastructure.storage.MinioStorage;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
@Configuration
@EnableConfigurationProperties({MediaStorageProperties.class, MediaProperties.class})
class MediaConfig {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }
    @Bean
    RetryTemplate thumbnailGenerationRetryTemplate() {
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(3)
                .delay(Duration.ofMillis(500))
                .multiplier(2.0)
                .jitter(Duration.ofMillis(100))
                .maxDelay(Duration.ofSeconds(5))
                .build();
        return new RetryTemplate(retryPolicy);
    }
    @Bean
    @ConditionalOnProperty(name = "media.storage.type", havingValue = "local", matchIfMissing = true)
    MediaStorage mediaStorage(LocalFilesystemStorage local) {
        return local;
    }
    @Bean
    @ConditionalOnMissingBean
    LocalFilesystemStorage localFilesystemStorage(MediaStorageProperties properties) {
        return new LocalFilesystemStorage(Path.of(properties.local().baseDir()));
    }
    @Bean
    @ConditionalOnProperty(name = "media.storage.type", havingValue = "minio")
    MediaStorage mediaStorage(MinioStorage minio) {
        return minio;
    }
    @Bean
    @ConditionalOnMissingBean
    MinioStorage minioStorage(MinioClient client, MediaStorageProperties properties) {
        return new MinioStorage(client, properties.minio().bucket());
    }
    @Bean
    @ConditionalOnProperty(name = "media.storage.type", havingValue = "minio")
    MinioClient minioClient(MediaStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.minio().endpoint())
                .credentials(properties.minio().accessKey(), properties.minio().secretKey())
                .region(properties.minio().region())
                .build();
    }
}