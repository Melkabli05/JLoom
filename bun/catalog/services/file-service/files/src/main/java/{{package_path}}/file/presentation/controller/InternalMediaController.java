package {{package}}.file.presentation.controller;
import {{package}}.file.application.service.MediaService;
import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaPurpose;
import {{package}}.file.domain.model.MediaVisibility;
import {{package}}.file.infrastructure.configuration.MediaStorageProperties;
import {{package}}.file.infrastructure.storage.MediaNotFoundException;
import {{package}}.file.infrastructure.storage.MediaObject;
import {{package}}.file.presentation.dto.MediaUploadResponse;
import {{package}}.file.presentation.dto.PresignedUploadResponse;
import {{package}}.file.presentation.mapper.MediaMapper;
import {{package}}.file.presentation.support.MediaStreaming;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
@RestController
@RequestMapping("/internal/media")
class InternalMediaController {
    private final MediaService service;
    private final MediaMapper mapper;
    private final MediaStorageProperties storageProperties;
    private final String serviceKey;
    InternalMediaController(MediaService service, MediaMapper mapper,
                            MediaStorageProperties storageProperties,
                            @Value("${internal.service-key:}") String serviceKey) {
        this.service = service;
        this.mapper = mapper;
        this.storageProperties = storageProperties;
        this.serviceKey = serviceKey;
    }
    @PostMapping("/uploads")
    ResponseEntity<MediaUploadResponse> upload(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String providedKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "purpose", defaultValue = "INGEST") MediaPurpose purpose,
            @RequestParam(value = "visibility", defaultValue = "INTERNAL") MediaVisibility visibility) throws IOException {
        if (serviceKey.isBlank() || !serviceKey.equals(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        MediaAsset asset = service.upload(
                null, purpose, visibility,
                file.getOriginalFilename(),
                file.getInputStream(),
                file.getSize(),
                file.getContentType(),
                null);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toUploadResponse(asset));
    }
    @PostMapping("/presign")
    ResponseEntity<PresignedUploadResponse> presign(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String providedKey,
            @RequestParam("purpose") MediaPurpose purpose,
            @RequestParam(value = "visibility", defaultValue = "INTERNAL") MediaVisibility visibility,
            @RequestParam(value = "contentType", defaultValue = "application/octet-stream") String contentType,
            @RequestParam(value = "originalFilename", required = false) String originalFilename) {
        if (serviceKey.isBlank() || !serviceKey.equals(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        URI url = service.presignPut(null, purpose, visibility, contentType, originalFilename);
        String path = url.getPath();
        if (path.startsWith("/")) path = path.substring(1);
        return ResponseEntity.ok(new PresignedUploadResponse(
                url, path,
                Instant.now().plus(storageProperties.minio().presignTtl())));
    }
    @GetMapping("/{id}")
    ResponseEntity<Resource> download(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String providedKey,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        if (serviceKey.isBlank() || !serviceKey.equals(providedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        MediaAsset asset = service.find(id).orElseThrow(() -> new MediaNotFoundException(id.toString()));
        MediaObject object = service.download(id);
        return MediaStreaming.stream(asset.getContentType(), asset.getOriginalFilename(),
                asset.getSizeBytes(), "private, max-age=300", object, rangeHeader);
    }
}