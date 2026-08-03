package com.faithlog.media.service.port;

import com.faithlog.media.domain.entity.MediaAsset;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface MediaAssetRepositoryPort {
	MediaAsset save(MediaAsset asset);
	Optional<MediaAsset> findByCampusIdAndId(Long campusId, Long assetId);
	Optional<MediaAsset> findByCampusIdAndIdForUpdate(Long campusId, Long assetId);
	Optional<MediaAsset> findByIdForUpdate(Long assetId);
	List<MediaAsset> findByCampusIdAndIdIn(Long campusId, List<Long> assetIds);
	List<MediaAsset> findByCampusIdAndIdInForUpdate(Long campusId, List<Long> assetIds);
	List<Long> findCleanupCandidateIds(Instant expiresAt, Instant orphanedBefore, int limit);
	void delete(MediaAsset asset);
}
