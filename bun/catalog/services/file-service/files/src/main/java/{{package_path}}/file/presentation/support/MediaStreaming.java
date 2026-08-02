package {{package}}.file.presentation.support;

import {{package}}.file.infrastructure.storage.MediaObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

public final class MediaStreaming {

    private MediaStreaming() {
    }

    public static ResponseEntity<Resource> stream(String contentType, String originalFilename,
                                                  long sizeBytes, String cacheControl,
                                                  MediaObject object, String rangeHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        if (object.checksum() != null && !object.checksum().isBlank()) {
            headers.setETag("\"" + object.checksum() + "\"");
        }
        headers.setCacheControl(cacheControl);
        if (originalFilename != null && !originalFilename.isBlank()) {
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename(originalFilename.replace("\"", "")).build());
        }

        if (rangeHeader == null || rangeHeader.isBlank()) {
            headers.setContentLength(sizeBytes);
            return ResponseEntity.ok().headers(headers)
                    .body(new InputStreamResource(object.content()));
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }
        if (ranges.size() != 1) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(sizeBytes);
        long end = range.getRangeEnd(sizeBytes);
        if (start >= sizeBytes || end >= sizeBytes) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }
        long contentLength = end - start + 1;
        headers.setContentLength(contentLength);
        headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + sizeBytes);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .headers(headers)
                .body(new InputStreamResource(skip(object.content(), start)));
    }

    private static InputStream skip(InputStream in, long n) {
        try {
            long skipped = in.skip(n);
            while (skipped < n) {
                long more = in.skip(n - skipped);
                if (more <= 0) break;
                skipped += more;
            }
            return in;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}