package com.faithlog.announcement.service;

import com.faithlog.announcement.domain.entity.AnnouncementDocument;
import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.service.port.PollMediaAttachmentPort;
import com.faithlog.announcement.service.port.WeeklyMaterialMediaAttachmentPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnnouncementDocumentAttachmentService {
	private static final int VALIDATION_BATCH_SIZE = 100;
	private final AnnouncementDocumentRepository documents;
	private final AnnouncementImageRepository images;
	private final MediaAssetRepositoryPort assets;
	private final PollMediaAttachmentPort pollAttachments;
	private final WeeklyMaterialMediaAttachmentPort weeklyAttachments;

	public AnnouncementDocumentAttachmentService(
		AnnouncementDocumentRepository documents,
		AnnouncementImageRepository images,
		MediaAssetRepositoryPort assets,
		PollMediaAttachmentPort pollAttachments,
		WeeklyMaterialMediaAttachmentPort weeklyAttachments
	) {
		this.documents = documents;
		this.images = images;
		this.assets = assets;
		this.pollAttachments = pollAttachments;
		this.weeklyAttachments = weeklyAttachments;
	}

	public void replace(Long announcementId, Long campusId, Long ownerId, List<Long> orderedAssetIds) {
		List<Long> requested = orderedAssetIds == null ? List.of() : List.copyOf(orderedAssetIds);
		if (requested.stream().anyMatch(id -> id == null || id <= 0)
			|| new HashSet<>(requested).size() != requested.size()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		List<AnnouncementDocument> existing = documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(announcementId);
		HashSet<Long> existingIds = existing.stream().map(AnnouncementDocument::mediaAssetId)
			.collect(Collectors.toCollection(HashSet::new));
		List<Long> sorted = requested.stream().sorted().toList();
		List<MediaAsset> loaded = new ArrayList<>();
		HashSet<Long> conflicts = new HashSet<>();
		for (int start = 0; start < sorted.size(); start += VALIDATION_BATCH_SIZE) {
			List<Long> batch = sorted.subList(start, Math.min(start + VALIDATION_BATCH_SIZE, sorted.size()));
			loaded.addAll(assets.findByCampusIdAndIdInForUpdate(campusId, batch));
			conflicts.addAll(weeklyAttachments.findAttachedAssetIds(batch));
			conflicts.addAll(documents.findAttachedAssetIdsForOtherAnnouncements(announcementId, batch));
			conflicts.addAll(images.findAttachedAssetIds(batch));
			conflicts.addAll(pollAttachments.findAttachedAssetIds(batch));
		}
		Map<Long, MediaAsset> byId = new LinkedHashMap<>();
		loaded.forEach(asset -> byId.put(asset.id(), asset));
		if (byId.size() != requested.size() || requested.stream().anyMatch(id -> {
			MediaAsset asset = byId.get(id);
			return asset == null || asset.kind() != MediaAssetKind.PDF || asset.status() != MediaAssetStatus.READY
				|| (!existingIds.contains(id) && !asset.ownerUserId().equals(ownerId));
		})) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		if (!conflicts.isEmpty()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		}
		HashSet<Long> requestedSet = new HashSet<>(requested);
		existing.stream().filter(document -> !requestedSet.contains(document.mediaAssetId())).forEach(document ->
			assets.findByCampusIdAndIdForUpdate(campusId, document.mediaAssetId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ASSET_NOT_FOUND)).markOrphaned());
		documents.deleteByAnnouncementId(announcementId);
		documents.flush();
		for (int index = 0; index < requested.size(); index++) {
			documents.save(AnnouncementDocument.create(campusId, announcementId, requested.get(index), index));
		}
	}

	public void orphanAll(Long announcementId, Long campusId) {
		List<AnnouncementDocument> existing = documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(announcementId);
		if (existing.isEmpty()) {
			documents.deleteByAnnouncementId(announcementId);
			documents.flush();
			return;
		}
		List<Long> sortedIds = existing.stream().map(AnnouncementDocument::mediaAssetId).sorted().toList();
		Map<Long, MediaAsset> lockedById = new LinkedHashMap<>();
		for (int start = 0; start < sortedIds.size(); start += VALIDATION_BATCH_SIZE) {
			List<Long> batch = sortedIds.subList(start, Math.min(start + VALIDATION_BATCH_SIZE, sortedIds.size()));
			assets.findByCampusIdAndIdInForUpdate(campusId, batch)
				.forEach(asset -> lockedById.put(asset.id(), asset));
		}
		if (lockedById.size() != sortedIds.size()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		for (Long assetId : sortedIds) {
			MediaAsset asset = lockedById.get(assetId);
			if (asset == null || asset.kind() != MediaAssetKind.PDF || asset.status() != MediaAssetStatus.READY) {
				throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
			}
			asset.markOrphaned();
		}
		documents.deleteByAnnouncementId(announcementId);
		documents.flush();
	}

	public List<Long> getOrderedAssetIds(Long announcementId) {
		return documents.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(announcementId).stream()
			.map(AnnouncementDocument::mediaAssetId).toList();
	}

	public Map<Long, List<Long>> getOrderedAssetIdsByAnnouncementIds(List<Long> announcementIds) {
		if (announcementIds.isEmpty()) return Map.of();
		Map<Long, List<Long>> result = new LinkedHashMap<>();
		announcementIds.forEach(id -> result.put(id, new ArrayList<>()));
		documents.findByAnnouncementIdInOrderByAnnouncementIdAscDisplayOrderAscIdAsc(announcementIds)
			.forEach(document -> result.get(document.announcementId()).add(document.mediaAssetId()));
		return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
			entry -> List.copyOf(entry.getValue()), (left, right) -> left, LinkedHashMap::new));
	}
}
