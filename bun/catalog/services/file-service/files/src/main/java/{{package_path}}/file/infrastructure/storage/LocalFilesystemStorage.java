package {{package}}.file.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
public class LocalFilesystemStorage implements MediaStorage {
    private final Path baseDir;
    public LocalFilesystemStorage(Path baseDir) {
        this.baseDir = baseDir;
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create media storage dir: " + baseDir, e);
        }
    }
    @Override
    public CompletableFuture<Void> putAsync(String key, InputStream content, long contentLength, String contentType) {
        Path target = resolve(key);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream digesting = new DigestInputStream(content, digest);
                 OutputStream out = Files.newOutputStream(tmp)) {
                digesting.transferTo(out);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            String checksum = HexFormat.of().formatHex(digest.digest());
            writeSidecarChecksum(target, checksum);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write media key: " + key, e);
        }
        return CompletableFuture.completedFuture(null);
    }
    @Override
    public MediaObject get(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            throw new MediaNotFoundException(key);
        }
        try {
            long size = Files.size(target);
            String contentType = Files.probeContentType(target);
            String checksum = readSidecarChecksum(target);
            return new MediaObject(Files.newInputStream(target),
                    contentType != null ? contentType : "application/octet-stream",
                    size, checksum);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read media key: " + key, e);
        }
    }
    private String readSidecarChecksum(Path target) {
        Path sidecar = target.resolveSibling(target.getFileName() + ".sha256");
        if (Files.exists(sidecar)) {
            try {
                return Files.readString(sidecar).trim();
            } catch (IOException e) {
                return "";
            }
        }
        return "";
    }
    private void writeSidecarChecksum(Path target, String checksum) {
        Path sidecar = target.resolveSibling(target.getFileName() + ".sha256");
        try {
            Files.writeString(sidecar, checksum);
        } catch (IOException e) {
        }
    }
    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }
    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete media key: " + key, e);
        }
    }
    @Override
    public URI presignedPutUrl(String key, Duration ttl, String contentType) {
        throw new UnsupportedOperationException("Local filesystem storage does not support presigned URLs");
    }
    @Override
    public URI presignedGetUrl(String key, Duration ttl) {
        throw new UnsupportedOperationException("Local filesystem storage does not support presigned URLs");
    }
    private Path resolve(String key) {
        Path p = baseDir.resolve(key).normalize();
        if (!p.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid media key: " + key);
        }
        return p;
    }
}
