package com.faithlog.media.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import com.faithlog.media.service.result.MediaAccessUrlResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MediaAssetQueryService {
	private static final int MAX_BATCH = 100;
	private final MediaAssetAccessSnapshotService snapshotService;
	private final MediaObjectStoragePort storage;
	private final Clock clock;

	public MediaAssetQueryService(
		MediaAssetAccessSnapshotService snapshotService,
		MediaObjectStoragePort storage,
		Clock clock
	) {
		this.snapshotService = snapshotService;
		this.storage = storage;
		this.clock = clock;
	}

	public List<MediaAccessUrlResult> getAccessUrls(Long campusId, Long requesterId, List<Long> orderedIds) {
		if (orderedIds == null || orderedIds.isEmpty() || orderedIds.size() > MAX_BATCH
			|| new HashSet<>(orderedIds).size() != orderedIds.size()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		var snapshots = snapshotService.authorize(campusId, requesterId, orderedIds);
		Instant expiresAt = clock.instant().plus(Duration.ofMinutes(10));
		return snapshots.stream().map(snapshot -> {
			return new MediaAccessUrlResult(snapshot.assetId(), snapshot.sha256(),
				storage.presignDownload(snapshot.thumbnailObjectKey()),
				storage.presignDownload(snapshot.detailObjectKey()), expiresAt);
		}).toList();
	}
}
