package {{package}}.file.infrastructure.storage;

public class MediaStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}