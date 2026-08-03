package com.faithlog.media.service;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MediaAssetCleanupService {

	private static final int BATCH_SIZE = 100;
	private static final Duration ORPHAN_RETENTION = Duration.ofHours(24);

	private final MediaAssetRepositoryPort repository;
	private final MediaObjectStoragePort storage;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public MediaAssetCleanupService(
		MediaAssetRepositoryPort repository,
		MediaObjectStoragePort storage,
		PlatformTransactionManager transactionManager,
		Clock clock
	) {
		this.repository = repository;
		this.storage = storage;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public int cleanupBatch() {
		Instant now = clock.instant();
		Instant orphanedBefore = now.minus(ORPHAN_RETENTION);
		List<Long> candidateIds = repository.findCleanupCandidateIds(now, orphanedBefore, BATCH_SIZE);
		int cleaned = 0;
		for (Long candidateId : candidateIds) {
			CleanupSnapshot snapshot = transactionTemplate.execute(
				status -> claim(candidateId, now, orphanedBefore));
			if (snapshot == null) {
				continue;
			}
			try {
				snapshot.objectKeys().forEach(storage::deleteObject);
			} catch (RuntimeException exception) {
				continue;
			}
			Boolean deleted = transactionTemplate.execute(status -> deleteIfUnchanged(snapshot, now, orphanedBefore));
			if (Boolean.TRUE.equals(deleted)) {
				cleaned++;
			}
		}
		return cleaned;
	}

	private CleanupSnapshot claim(Long assetId, Instant now, Instant orphanedBefore) {
		return repository.findByIdForUpdate(assetId)
			.filter(asset -> eligible(asset, now, orphanedBefore))
			.map(asset -> new CleanupSnapshot(asset.id(), asset.status(), objectKeys(asset)))
			.orElse(null);
	}

	private boolean deleteIfUnchanged(CleanupSnapshot snapshot, Instant now, Instant orphanedBefore) {
		return repository.findByIdForUpdate(snapshot.assetId())
			.filter(asset -> asset.status() == snapshot.status())
			.filter(asset -> eligible(asset, now, orphanedBefore))
			.filter(asset -> Objects.equals(objectKeys(asset), snapshot.objectKeys()))
			.map(asset -> {
				if (asset.status() == MediaAssetStatus.READY) {
					asset.clearTemporaryObjectKey();
				} else {
					repository.delete(asset);
				}
				return true;
			})
			.orElse(false);
	}

	private boolean eligible(MediaAsset asset, Instant now, Instant orphanedBefore) {
		if (asset.status() == MediaAssetStatus.READY
			&& asset.temporaryObjectKey() != null
			&& !asset.expiresAt().isAfter(now)) {
			return true;
		}
		if ((asset.status() == MediaAssetStatus.PENDING || asset.status() == MediaAssetStatus.FAILED)
			&& !asset.expiresAt().isAfter(now)) {
			return true;
		}
		return asset.status() == MediaAssetStatus.ORPHANED
			&& asset.orphanedAt() != null
			&& !asset.orphanedAt().isAfter(orphanedBefore);
	}

	private List<String> objectKeys(MediaAsset asset) {
		if (asset.status() == MediaAssetStatus.READY) {
			return asset.temporaryObjectKey() == null ? List.of() : List.of(asset.temporaryObjectKey());
		}
		return java.util.stream.Stream.of(
				asset.temporaryObjectKey(), asset.thumbnailObjectKey(), asset.detailObjectKey())
			.filter(Objects::nonNull)
			.toList();
	}

	private record CleanupSnapshot(Long assetId, MediaAssetStatus status, List<String> objectKeys) {
		private CleanupSnapshot {
			objectKeys = List.copyOf(objectKeys);
		}
	}
}
