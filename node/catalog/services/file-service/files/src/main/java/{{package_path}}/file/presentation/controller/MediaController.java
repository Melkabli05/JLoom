package {{package}}.file.presentation.controller;

import {{package}}.file.application.service.MediaService;
import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaPurpose;
import {{package}}.file.domain.model.MediaVisibility;
import {{package}}.file.infrastructure.configuration.MediaProperties;
import {{package}}.file.infrastructure.storage.MediaNotFoundException;
import {{package}}.file.infrastructure.storage.MediaObject;
import {{package}}.file.presentation.dto.MediaUploadResponse;
import {{package}}.file.presentation.mapper.MediaMapper;
import {{package}}.file.presentation.support.MediaStreaming;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

@RestController
@RequestMapping("/media")
class MediaController {

    private final MediaService service;
    private final MediaMapper mapper;
    private final String adminAuthority;

    MediaController(MediaService service, MediaMapper mapper, MediaProperties mediaProperties) {
        this.service = service;
        this.mapper = mapper;
        this.adminAuthority = mediaProperties.adminAuthority();
    }

    @PostMapping("/uploads")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<MediaUploadResponse> upload(
            JwtAuthenticationToken token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") MediaPurpose purpose,
            @RequestParam(value = "visibility", defaultValue = "PRIVATE") MediaVisibility visibility,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) throws IOException {
        UUID ownerId = UUID.fromString(token.getName());
        MediaAsset asset = service.upload(
                ownerId, purpose, visibility,
                file.getOriginalFilename(),
                file.getInputStream(),
                file.getSize(),
                file.getContentType(),
                idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toUploadResponse(asset));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<Resource> download(
            JwtAuthenticationToken token,
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        MediaAsset asset = service.find(id)
                .orElseThrow(() -> new MediaNotFoundException(id.toString()));
        if (asset.getVisibility() == MediaVisibility.INTERNAL) {
            throw new MediaNotFoundException(id.toString());
        }
        if (asset.getVisibility() == MediaVisibility.PRIVATE) {
            boolean isOwner = token.getName().equals(asset.getOwnerId().toString());
            boolean isAdmin = hasAuthority(adminAuthority, token.getAuthorities());
            if (!isOwner && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        MediaObject object = service.download(id);
        return MediaStreaming.stream(asset.getContentType(), asset.getOriginalFilename(),
                asset.getSizeBytes(), cacheControl(asset.getVisibility()),
                object, rangeHeader);
    }

    private static String cacheControl(MediaVisibility visibility) {
        return visibility == MediaVisibility.PUBLIC ? "public, max-age=3600" : "private, max-age=3600";
    }

    private static boolean hasAuthority(String authority, Collection<? extends GrantedAuthority> authorities) {
        if (authority == null || authority.isBlank()) return false;
        String bare = authority.startsWith("ROLE_") ? authority.substring(5) : authority;
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals(authority) || a.equals(bare));
    }
}