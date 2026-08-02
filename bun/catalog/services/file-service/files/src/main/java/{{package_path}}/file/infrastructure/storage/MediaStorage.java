package {{package}}.file.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
public interface MediaStorage {
    default void put(String key, InputStream content, long contentLength, String contentType) {
        putAsync(key, content, contentLength, contentType).join();
    }
    CompletableFuture<Void> putAsync(String key, InputStream content, long contentLength, String contentType);
    MediaObject get(String key);
    boolean exists(String key);
    void delete(String key);
    URI presignedPutUrl(String key, Duration ttl, String contentType);
    URI presignedGetUrl(String key, Duration ttl);
}
