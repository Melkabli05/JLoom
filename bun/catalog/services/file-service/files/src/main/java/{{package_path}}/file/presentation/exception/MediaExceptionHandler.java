package {{package}}.file.presentation.exception;

import {{package}}.file.application.exception.PayloadTooLargeException;
import {{package}}.file.application.exception.PresignUnsupportedException;
import {{package}}.file.application.exception.UnsupportedMediaTypeException;
import {{package}}.file.infrastructure.storage.MediaNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice
public class MediaExceptionHandler {
    @ExceptionHandler(MediaNotFoundException.class)
    public ProblemDetail handleNotFound(MediaNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Media not found", ex.getMessage());
    }
    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail handlePayloadTooLarge(PayloadTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", ex.getMessage());
    }
    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ProblemDetail handleUnsupportedMediaType(UnsupportedMediaTypeException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", ex.getMessage());
    }
    @ExceptionHandler(PresignUnsupportedException.class)
    public ProblemDetail handlePresignUnsupported(PresignUnsupportedException ex) {
        return problem(HttpStatus.NOT_IMPLEMENTED, "Presigned URLs unsupported", ex.getMessage());
    }
    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }
}
