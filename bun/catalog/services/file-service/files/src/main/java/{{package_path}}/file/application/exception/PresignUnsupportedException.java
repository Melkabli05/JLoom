package {{package}}.file.application.exception;
public class PresignUnsupportedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PresignUnsupportedException(String message) {
        super(message);
    }
}