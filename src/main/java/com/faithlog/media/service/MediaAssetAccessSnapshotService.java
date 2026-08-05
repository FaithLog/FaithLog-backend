package com.faithlog.media.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.policy.MediaAssetAccessPolicy;
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
		var readableWeeklyIds = accessPolicy.readableWeeklyMaterialAssetIds(campusId, requesterId, orderedIds);
		var byId = new LinkedHashMap<Long, com.faithlog.media.domain.entity.MediaAsset>();
		assets.findByIdIn(orderedIds).forEach(asset -> byId.put(asset.id(), asset));
		if (byId.size() != orderedIds.size() || orderedIds.stream().anyMatch(id -> {
			var asset = byId.get(id);
			boolean sameMediaTenant = asset != null && campusId.equals(asset.campusId());
			return asset == null || asset.status() != MediaAssetStatus.READY
				|| (sameMediaTenant && !readableIds.contains(id)
					&& !(asset.ownerUserId().equals(requesterId)
						&& accessPolicy.canPreviewOwnedPollAsset(campusId, requesterId)))
				|| (!sameMediaTenant && !readableWeeklyIds.contains(id));
		})) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN);
		}
		return orderedIds.stream().map(id -> {
			var asset = byId.get(id);
			return new AccessSnapshot(asset.id(), asset.kind(), asset.inputContentType(), asset.originalFileName(),
				asset.outputByteSize(), asset.outputSha256(), asset.thumbnailObjectKey(), asset.detailObjectKey(),
				asset.documentObjectKey());
		}).toList();
	}

	public record AccessSnapshot(
		Long assetId,
		com.faithlog.media.domain.type.MediaAssetKind kind,
		String contentType,
		String fileName,
		Long byteSize,
		String sha256,
		String thumbnailObjectKey,
		String detailObjectKey,
		String documentObjectKey
	) {
		public AccessSnapshot(Long assetId, String sha256, String thumbnailObjectKey, String detailObjectKey) {
			this(assetId, com.faithlog.media.domain.type.MediaAssetKind.IMAGE, "image/jpeg", null, null,
				sha256, thumbnailObjectKey, detailObjectKey, null);
		}
	}
}
