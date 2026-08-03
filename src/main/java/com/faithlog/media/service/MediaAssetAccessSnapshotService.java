package com.faithlog.media.service;

import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaAssetAccessSnapshotService {

	private final MediaAssetRepositoryPort assets;
	private final AnnouncementImageRepository images;
	private final AnnouncementAccessPolicy accessPolicy;

	public MediaAssetAccessSnapshotService(
		MediaAssetRepositoryPort assets,
		AnnouncementImageRepository images,
		AnnouncementAccessPolicy accessPolicy
	) {
		this.assets = assets;
		this.images = images;
		this.accessPolicy = accessPolicy;
	}

	@Transactional(readOnly = true)
	public List<AccessSnapshot> authorize(Long campusId, Long requesterId, List<Long> orderedIds) {
		boolean manager = isManager(campusId, requesterId);
		if (!manager) {
			accessPolicy.requireActiveMember(campusId, requesterId);
			if (new HashSet<>(images.findPublishedAttachedAssetIds(campusId, orderedIds)).size() != orderedIds.size()) {
				throw new BusinessException(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN);
			}
		}
		var byId = new LinkedHashMap<Long, com.faithlog.media.domain.entity.MediaAsset>();
		assets.findByCampusIdAndIdIn(campusId, orderedIds).forEach(asset -> byId.put(asset.id(), asset));
		if (byId.size() != orderedIds.size() || orderedIds.stream().anyMatch(id -> {
			var asset = byId.get(id);
			return asset == null || asset.status() != MediaAssetStatus.READY;
		})) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN);
		}
		return orderedIds.stream().map(id -> {
			var asset = byId.get(id);
			return new AccessSnapshot(asset.id(), asset.outputSha256(), asset.thumbnailObjectKey(), asset.detailObjectKey());
		}).toList();
	}

	private boolean isManager(Long campusId, Long requesterId) {
		try {
			accessPolicy.requireManager(campusId, requesterId);
			return true;
		} catch (BusinessException exception) {
			if (exception.errorCode() != ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN) {
				throw exception;
			}
			return false;
		}
	}

	public record AccessSnapshot(Long assetId, String sha256, String thumbnailObjectKey, String detailObjectKey) {
	}
}
