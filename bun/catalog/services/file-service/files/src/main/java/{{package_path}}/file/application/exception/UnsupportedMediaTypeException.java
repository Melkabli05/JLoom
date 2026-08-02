package {{package}}.file.application.exception;

import java.util.Set;
public class UnsupportedMediaTypeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String claimed;
    private final Set<String> allowed;
    public UnsupportedMediaTypeException(String claimed, Set<String> allowed) {
        super("Unsupported content type: " + claimed + " (allowed: " + allowed + ")");
        this.claimed = claimed;
        this.allowed = allowed;
    }
    public String claimed() { return claimed; }
    public Set<String> allowed() { return allowed; }
}
