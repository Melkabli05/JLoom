package {{package}}.file.infrastructure.storage;
public class MediaNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public MediaNotFoundException(String key) {
        super("Media not found: " + key);
    }
}
