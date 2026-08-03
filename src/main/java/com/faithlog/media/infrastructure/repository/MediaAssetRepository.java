package com.faithlog.media.infrastructure.repository;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long>, MediaAssetRepositoryPort {
	Optional<MediaAsset> findByCampusIdAndId(Long campusId, Long assetId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select asset from MediaAsset asset where asset.campusId = :campusId and asset.id = :assetId")
	Optional<MediaAsset> findByCampusIdAndIdForUpdate(@Param("campusId") Long campusId, @Param("assetId") Long assetId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select asset from MediaAsset asset where asset.id = :assetId")
	Optional<MediaAsset> findByIdForUpdate(@Param("assetId") Long assetId);

	@Query("select asset from MediaAsset asset where asset.campusId = :campusId and asset.id in :assetIds order by asset.id")
	List<MediaAsset> findByCampusIdAndIdIn(@Param("campusId") Long campusId, @Param("assetIds") List<Long> assetIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select asset from MediaAsset asset where asset.campusId = :campusId and asset.id in :assetIds order by asset.id")
	List<MediaAsset> findByCampusIdAndIdInForUpdate(
		@Param("campusId") Long campusId,
		@Param("assetIds") List<Long> assetIds
	);

	@Query(value = """
		select id from media_assets
		where (status in ('PENDING', 'FAILED') and expires_at <= :expiresAt)
			or (status = 'READY' and temporary_object_key is not null and expires_at <= :expiresAt)
			or (status = 'ORPHANED' and orphaned_at <= :orphanedBefore)
		order by id
		limit :limit
		""", nativeQuery = true)
	List<Long> findCleanupCandidateIds(
		@Param("expiresAt") Instant expiresAt,
		@Param("orphanedBefore") Instant orphanedBefore,
		@Param("limit") int limit
	);
}
