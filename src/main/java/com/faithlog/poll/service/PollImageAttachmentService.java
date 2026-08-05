package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.poll.domain.entity.PollImage;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.poll.infrastructure.repository.PollDocumentRepository;
import com.faithlog.poll.service.port.AnnouncementMediaAttachmentPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PollImageAttachmentService {

	private static final int VALIDATION_BATCH_SIZE = 100;

	private final PollImageRepository images;
	private final PollDocumentRepository documents;
	private final MediaAssetRepositoryPort assets;
	private final AnnouncementMediaAttachmentPort announcementImages;

	public PollImageAttachmentService(
		PollImageRepository images,
		PollDocumentRepository documents,
		MediaAssetRepositoryPort assets,
		AnnouncementMediaAttachmentPort announcementImages
	) {
		this.images = images;
		this.documents = documents;
		this.assets = assets;
		this.announcementImages = announcementImages;
	}

	public void replace(Long pollId, Long campusId, Long ownerId, List<Long> orderedAssetIds) {
		List<Long> requested = orderedAssetIds == null ? List.of() : List.copyOf(orderedAssetIds);
		if (requested.stream().anyMatch(id -> id == null || id <= 0)
			|| new HashSet<>(requested).size() != requested.size()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		List<PollImage> existing = images.findByPollIdOrderByDisplayOrderAscIdAsc(pollId);
		HashSet<Long> existingAssetIds = existing.stream()
			.map(PollImage::mediaAssetId)
			.collect(Collectors.toCollection(HashSet::new));
		List<Long> sortedIds = requested.stream().sorted().toList();
		List<MediaAsset> loaded = new ArrayList<>();
		HashSet<Long> conflictingIds = new HashSet<>();
		for (int start = 0; start < sortedIds.size(); start += VALIDATION_BATCH_SIZE) {
			List<Long> batch = sortedIds.subList(start, Math.min(start + VALIDATION_BATCH_SIZE, sortedIds.size()));
			loaded.addAll(assets.findByCampusIdAndIdInForUpdate(campusId, batch));
			conflictingIds.addAll(images.findAttachedAssetIdsForOtherPolls(pollId, batch));
			conflictingIds.addAll(documents.findAttachedAssetIds(batch));
			conflictingIds.addAll(announcementImages.findAttachedAssetIds(batch));
		}
		Map<Long, MediaAsset> byId = new LinkedHashMap<>();
		loaded.forEach(asset -> byId.put(asset.id(), asset));
		if (byId.size() != requested.size() || requested.stream().anyMatch(id -> {
			MediaAsset asset = byId.get(id);
			return asset == null
				|| asset.kind() != MediaAssetKind.IMAGE
				|| asset.status() != MediaAssetStatus.READY
				|| (!existingAssetIds.contains(id) && !asset.ownerUserId().equals(ownerId));
		})) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		if (!conflictingIds.isEmpty()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		}
		HashSet<Long> requestedSet = new HashSet<>(requested);
		existing.stream().filter(image -> !requestedSet.contains(image.mediaAssetId())).forEach(image ->
			assets.findByCampusIdAndIdForUpdate(campusId, image.mediaAssetId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ASSET_NOT_FOUND))
				.markOrphaned());
		images.deleteByPollId(pollId);
		images.flush();
		for (int index = 0; index < requested.size(); index++) {
			images.save(PollImage.create(campusId, pollId, requested.get(index), index));
		}
	}

	public List<Long> getOrderedAssetIds(Long pollId) {
		return images.findByPollIdOrderByDisplayOrderAscIdAsc(pollId).stream()
			.map(PollImage::mediaAssetId)
			.toList();
	}

	public Map<Long, List<Long>> getOrderedAssetIdsByPollIds(List<Long> pollIds) {
		if (pollIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<Long>> result = new LinkedHashMap<>();
		pollIds.forEach(id -> result.put(id, new ArrayList<>()));
		images.findByPollIdInOrderByPollIdAscDisplayOrderAscIdAsc(pollIds)
			.forEach(image -> result.computeIfAbsent(image.pollId(), ignored -> new ArrayList<>())
				.add(image.mediaAssetId()));
		return result.entrySet().stream().collect(Collectors.toMap(
			Map.Entry::getKey,
			entry -> List.copyOf(entry.getValue()),
			(left, right) -> left,
			LinkedHashMap::new
		));
	}
}
