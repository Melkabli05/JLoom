package {{package}}.file.application.service;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
class MediaValidatorTest {
    private final MediaValidator validator = new MediaValidator();
    @Test
    void detectsJpeg() throws IOException {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
        assertThat(validator.sniff(new ByteArrayInputStream(jpeg))).isEqualTo("image/jpeg");
    }
    @Test
    void detectsPng() throws IOException {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(validator.sniff(new ByteArrayInputStream(png))).isEqualTo("image/png");
    }
    @Test
    void detectsGif() throws IOException {
        byte[] gif = new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
        assertThat(validator.sniff(new ByteArrayInputStream(gif))).isEqualTo("image/gif");
    }
    @Test
    void detectsWebp() throws IOException {
        byte[] webp = new byte[]{
                0x52, 0x49, 0x46, 0x46,
                0, 0, 0, 0,
                0x57, 0x45, 0x42, 0x50
        };
        assertThat(validator.sniff(new ByteArrayInputStream(webp))).isEqualTo("image/webp");
    }
    @Test
    void detectsPdf() throws IOException {
        byte[] pdf = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
        assertThat(validator.sniff(new ByteArrayInputStream(pdf))).isEqualTo("application/pdf");
    }
    @Test
    void detectsWebm() throws IOException {
        byte[] webm = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};
        assertThat(validator.sniff(new ByteArrayInputStream(webm))).isEqualTo("video/webm");
    }
    @Test
    void detectsMp4() throws IOException {
        byte[] mp4 = new byte[]{0, 0, 0, 0, 0x66, 0x74, 0x79, 0x70};
        assertThat(validator.sniff(new ByteArrayInputStream(mp4))).isEqualTo("video/mp4");
    }
    @Test
    void returnsNullForUnknown() throws IOException {
        byte[] unknown = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        assertThat(validator.sniff(new ByteArrayInputStream(unknown))).isNull();
    }
    @Test
    void allowsAllSevenTypes() {
        Set<String> allowed = validator.allowedContentTypes();
        assertThat(allowed).containsExactlyInAnyOrder(
                "image/jpeg", "image/png", "image/webp", "image/gif",
                "application/pdf", "video/mp4", "video/webm");
    }
}
