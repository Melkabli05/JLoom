package {{package}}.file.infrastructure.storage;
import java.io.InputStream;
public record MediaObject(InputStream content, String contentType, long sizeBytes, String checksum) {
}