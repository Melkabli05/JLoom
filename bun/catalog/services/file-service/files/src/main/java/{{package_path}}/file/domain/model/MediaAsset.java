package {{package}}.file.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MediaAsset {

    @Id
    private UUID id;

    @Column(name = "storage_key", nullable = false, length = 1024, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 64)
    private String purpose;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private MediaVisibility visibility;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private MediaAssetStatus status;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "thumbnail_key", length = 1024)
    private String thumbnailKey;
}