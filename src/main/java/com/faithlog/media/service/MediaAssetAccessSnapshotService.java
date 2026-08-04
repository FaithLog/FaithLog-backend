package com.faithlog.media.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.policy.MediaAssetAccessPolicy;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaAssetAccessSnapshotService {

	private final MediaAssetRepositoryPort assets;
	private final MediaAssetAccessPolicy accessPolicy;

	public MediaAssetAccessSnapshotService(
		MediaAssetRepositoryPort assets,
		MediaAssetAccessPolicy accessPolicy
	) {
		this.assets = assets;
		this.accessPolicy = accessPolicy;
	}

	@Transactional(readOnly = true)
	public List<AccessSnapshot> authorize(Long campusId, Long requesterId, List<Long> orderedIds) {
		var readableIds = accessPolicy.readableAssetIds(campusId, requesterId, orderedIds);
		var byId = new LinkedHashMap<Long, com.faithlog.media.domain.entity.MediaAsset>();
		assets.findByCampusIdAndIdIn(campusId, orderedIds).forEach(asset -> byId.put(asset.id(), asset));
		if (byId.size() != orderedIds.size() || orderedIds.stream().anyMatch(id -> {
			var asset = byId.get(id);
			return asset == null || asset.status() != MediaAssetStatus.READY
				|| (!readableIds.contains(id)
					&& !(asset.ownerUserId().equals(requesterId)
						&& accessPolicy.canPreviewOwnedPollAsset(campusId, requesterId)));
		})) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN);
		}
		return orderedIds.stream().map(id -> {
			var asset = byId.get(id);
			return new AccessSnapshot(asset.id(), asset.outputSha256(), asset.thumbnailObjectKey(), asset.detailObjectKey());
		}).toList();
	}

	public record AccessSnapshot(Long assetId, String sha256, String thumbnailObjectKey, String detailObjectKey) {
	}
}
