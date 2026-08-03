package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.entity.AnnouncementImage;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.announcement.service.port.PollMediaAttachmentPort;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnnouncementImageAttachmentService {
	private static final int VALIDATION_BATCH_SIZE = 100;
	private final AnnouncementImageRepository images;
	private final MediaAssetRepositoryPort assets;
	private final PollMediaAttachmentPort pollImages;

	public AnnouncementImageAttachmentService(
		AnnouncementImageRepository images,
		MediaAssetRepositoryPort assets,
		PollMediaAttachmentPort pollImages
	) {
		this.images = images;
		this.assets = assets;
		this.pollImages = pollImages;
	}

	public void replace(Long announcementId, Long campusId, Long ownerId, List<Long> orderedAssetIds) {
		List<Long> requested = orderedAssetIds == null ? List.of() : List.copyOf(orderedAssetIds);
		if (requested.stream().anyMatch(id -> id == null || id <= 0)
			|| new HashSet<>(requested).size() != requested.size()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		var existing = images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(announcementId);
		var existingAssetIds = new HashSet<>(existing.stream().map(AnnouncementImage::mediaAssetId).toList());
		List<Long> sortedIds = requested.stream().sorted().toList();
		var loaded = new java.util.ArrayList<com.faithlog.media.domain.entity.MediaAsset>();
		var conflictingIds = new HashSet<Long>();
		for (int start = 0; start < sortedIds.size(); start += VALIDATION_BATCH_SIZE) {
			List<Long> batch = sortedIds.subList(start, Math.min(start + VALIDATION_BATCH_SIZE, sortedIds.size()));
			loaded.addAll(assets.findByCampusIdAndIdInForUpdate(campusId, batch));
			conflictingIds.addAll(images.findAttachedAssetIdsForOtherAnnouncements(announcementId, batch));
			conflictingIds.addAll(pollImages.findAttachedAssetIds(batch));
		}
		var byId = new LinkedHashMap<Long, com.faithlog.media.domain.entity.MediaAsset>();
		loaded.forEach(asset -> byId.put(asset.id(), asset));
		if (byId.size() != requested.size() || requested.stream().anyMatch(id -> {
			var asset = byId.get(id);
			return asset == null || asset.status() != MediaAssetStatus.READY
				|| (!existingAssetIds.contains(id) && !asset.ownerUserId().equals(ownerId));
		})) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		if (!conflictingIds.isEmpty()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		}
		var requestedSet = new HashSet<>(requested);
		existing.stream().filter(image -> !requestedSet.contains(image.mediaAssetId())).forEach(image -> {
			var asset = assets.findByCampusIdAndIdForUpdate(campusId, image.mediaAssetId()).orElseThrow();
			asset.markOrphaned();
		});
		images.deleteByAnnouncementId(announcementId);
		images.flush();
		for (int index = 0; index < requested.size(); index++) {
			images.save(AnnouncementImage.create(campusId, announcementId, requested.get(index), index));
		}
	}

	public List<Long> getOrderedAssetIds(Long announcementId) {
		return images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(announcementId).stream()
			.map(AnnouncementImage::mediaAssetId).toList();
	}

	public Map<Long, List<Long>> getOrderedAssetIdsByAnnouncementIds(List<Long> announcementIds) {
		if (announcementIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<Long>> result = new LinkedHashMap<>();
		announcementIds.forEach(id -> result.put(id, new java.util.ArrayList<>()));
		images.findByAnnouncementIdInOrderByAnnouncementIdAscDisplayOrderAscIdAsc(announcementIds)
			.forEach(image -> result.computeIfAbsent(image.announcementId(), ignored -> new java.util.ArrayList<>())
				.add(image.mediaAssetId()));
		return result.entrySet().stream().collect(java.util.stream.Collectors.toMap(
			Map.Entry::getKey,
			entry -> List.copyOf(entry.getValue()),
			(left, right) -> left,
			LinkedHashMap::new
		));
	}
}
