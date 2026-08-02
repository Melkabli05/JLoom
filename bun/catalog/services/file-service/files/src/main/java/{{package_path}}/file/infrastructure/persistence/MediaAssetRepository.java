package {{package}}.file.infrastructure.persistence;

import {{package}}.file.domain.model.MediaAsset;
import {{package}}.file.domain.model.MediaAssetStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID>, JpaSpecificationExecutor<MediaAsset> {
    Optional<MediaAsset> findByStorageKey(String storageKey);
    Optional<MediaAsset> findFirstByOwnerIdAndPurposeAndIdempotencyKeyAndStatusNot(
            UUID ownerId, String purpose, String idempotencyKey, MediaAssetStatus excludedStatus);
    List<MediaAsset> findByExpiresAtBeforeAndStatusNot(Instant cutoff, MediaAssetStatus excludedStatus, Pageable pageable);
    List<MediaAsset> findByStatus(MediaAssetStatus status, Pageable pageable);
}
