package {{package}}.file.infrastructure.storage;

import {{package}}.file.domain.model.MediaPurpose;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
public final class MediaKeyFactory {
    private static final DateTimeFormatter DAILY = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private MediaKeyFactory() {
    }
    public static String forUpload(MediaPurpose purpose, String originalFilename) {
        return purpose.name().toLowerCase().replace('_', '-')
                + "/" + LocalDate.now().format(DAILY)
                + "/" + UUID.randomUUID()
                + (originalFilename == null || originalFilename.isBlank() ? "" : "-" + sanitize(originalFilename));
    }
    public static String forThumbnail(String originalKey, int width, int height) {
        return "thumbnails/" + originalKey + "-" + width + "x" + height;
    }
    private static String sanitize(String filename) {
        String name = filename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        StringBuilder out = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            out.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        String result = out.toString().trim();
        return result.length() > 120 ? result.substring(result.length() - 120) : result;
    }
}
