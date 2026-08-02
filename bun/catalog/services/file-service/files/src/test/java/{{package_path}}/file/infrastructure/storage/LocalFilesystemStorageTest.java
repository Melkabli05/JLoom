package {{package}}.file.infrastructure.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class LocalFilesystemStorageTest {
    @TempDir
    Path tempDir;
    @Test
    void putGetRoundtrip() throws IOException {
        LocalFilesystemStorage storage = new LocalFilesystemStorage(tempDir);
        byte[] body = new byte[]{1, 2, 3, 4, 5};
        storage.put("foo/bar.bin", new ByteArrayInputStream(body), body.length, "application/octet-stream");
        MediaObject obj = storage.get("foo/bar.bin");
        assertThat(obj.sizeBytes()).isEqualTo(5L);
        assertThat(obj.contentType()).startsWith("application");
    }
    @Test
    void existsReturnsTrueAfterPutAndFalseAfterDelete() throws IOException {
        LocalFilesystemStorage storage = new LocalFilesystemStorage(tempDir);
        storage.put("k", new ByteArrayInputStream(new byte[]{1}), 1, "text/plain");
        assertThat(storage.exists("k")).isTrue();
        storage.delete("k");
        assertThat(storage.exists("k")).isFalse();
    }
    @Test
    void getOnMissingKeyThrowsMediaNotFound() {
        LocalFilesystemStorage storage = new LocalFilesystemStorage(tempDir);
        assertThatThrownBy(() -> storage.get("nope"))
                .isInstanceOf(MediaNotFoundException.class);
    }
    @Test
    void presignedPutUrlThrowsUnsupported() {
        LocalFilesystemStorage storage = new LocalFilesystemStorage(tempDir);
        assertThatThrownBy(() -> storage.presignedPutUrl("k", java.time.Duration.ofMinutes(1), "text/plain"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test
    void presignedGetUrlThrowsUnsupported() {
        LocalFilesystemStorage storage = new LocalFilesystemStorage(tempDir);
        assertThatThrownBy(() -> storage.presignedGetUrl("k", java.time.Duration.ofMinutes(1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
    @Test
    void pathTraversalRejected() {
        LocalFilesystemStorage storage = new LocalFilesystemStorage(tempDir);
        assertThatThrownBy(() -> storage.get("../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}