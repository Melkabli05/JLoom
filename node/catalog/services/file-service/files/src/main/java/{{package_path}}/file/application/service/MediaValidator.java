package {{package}}.file.application.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class MediaValidator {

    private static final int PEEK_SIZE = 16;

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf", "video/mp4", "video/webm");

    public Set<String> allowedContentTypes() {
        return ALLOWED;
    }

    public String sniff(InputStream peekable) throws IOException {
        byte[] buf = new byte[PEEK_SIZE];
        int read = 0;
        while (read < PEEK_SIZE) {
            int n = peekable.read(buf, read, PEEK_SIZE - read);
            if (n < 0) break;
            read += n;
        }
        return matchSniffed(buf, read);
    }

    public String matchSniffed(byte[] buf, int read) {
        if (read >= 3 && (buf[0] & 0xFF) == 0xFF && (buf[1] & 0xFF) == 0xD8 && (buf[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (read >= 8
                && (buf[0] & 0xFF) == 0x89 && buf[1] == 0x50 && buf[2] == 0x4E && buf[3] == 0x47
                && buf[4] == 0x0D && buf[5] == 0x0A && buf[6] == 0x1A && buf[7] == 0x0A) {
            return "image/png";
        }
        if (read >= 4 && buf[0] == 0x47 && buf[1] == 0x49 && buf[2] == 0x46 && buf[3] == 0x38) {
            return "image/gif";
        }
        if (read >= 12 && buf[0] == 0x52 && buf[1] == 0x49 && buf[2] == 0x46 && buf[3] == 0x46
                && buf[8] == 0x57 && buf[9] == 0x45 && buf[10] == 0x42 && buf[11] == 0x50) {
            return "image/webp";
        }
        if (read >= 4 && buf[0] == 0x25 && buf[1] == 0x50 && buf[2] == 0x44 && buf[3] == 0x46) {
            return "application/pdf";
        }
        if (read >= 4 && buf[0] == 0x1A && buf[1] == 0x45 && (buf[2] & 0xFF) == 0xDF && (buf[3] & 0xFF) == 0xA3) {
            return "video/webm";
        }
        if (read >= 8 && buf[4] == 0x66 && buf[5] == 0x74 && buf[6] == 0x79 && buf[7] == 0x70) {
            return "video/mp4";
        }
        return null;
    }
}