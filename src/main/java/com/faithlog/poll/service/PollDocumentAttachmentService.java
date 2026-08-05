package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.poll.domain.entity.PollDocument;
import com.faithlog.poll.infrastructure.repository.PollDocumentRepository;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.poll.service.port.AnnouncementMediaAttachmentPort;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PollDocumentAttachmentService {
	private static final int VALIDATION_BATCH_SIZE = 100;
	private final PollDocumentRepository documents;
	private final PollImageRepository images;
	private final MediaAssetRepositoryPort assets;
	private final AnnouncementMediaAttachmentPort announcementAttachments;

	public PollDocumentAttachmentService(
		PollDocumentRepository documents,
		PollImageRepository images,
		MediaAssetRepositoryPort assets,
		AnnouncementMediaAttachmentPort announcementAttachments
	) {
		this.documents = documents;
		this.images = images;
		this.assets = assets;
		this.announcementAttachments = announcementAttachments;
	}

	public void replace(Long pollId, Long campusId, Long ownerId, List<Long> orderedAssetIds) {
		List<Long> requested = orderedAssetIds == null ? List.of() : List.copyOf(orderedAssetIds);
		if (requested.stream().anyMatch(id -> id == null || id <= 0)
			|| new HashSet<>(requested).size() != requested.size()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		List<PollDocument> existing = documents.findByPollIdOrderByDisplayOrderAscIdAsc(pollId);
		HashSet<Long> existingIds = existing.stream().map(PollDocument::mediaAssetId)
			.collect(Collectors.toCollection(HashSet::new));
		List<Long> sorted = requested.stream().sorted().toList();
		List<MediaAsset> loaded = new ArrayList<>();
		HashSet<Long> conflicts = new HashSet<>();
		for (int start = 0; start < sorted.size(); start += VALIDATION_BATCH_SIZE) {
			List<Long> batch = sorted.subList(start, Math.min(start + VALIDATION_BATCH_SIZE, sorted.size()));
			loaded.addAll(assets.findByCampusIdAndIdInForUpdate(campusId, batch));
			conflicts.addAll(documents.findAttachedAssetIdsForOtherPolls(pollId, batch));
			conflicts.addAll(images.findAttachedAssetIds(batch));
			conflicts.addAll(announcementAttachments.findAttachedAssetIds(batch));
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
		if (!conflicts.isEmpty()) throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		HashSet<Long> requestedSet = new HashSet<>(requested);
		existing.stream().filter(document -> !requestedSet.contains(document.mediaAssetId())).forEach(document ->
			assets.findByCampusIdAndIdForUpdate(campusId, document.mediaAssetId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ASSET_NOT_FOUND)).markOrphaned());
		documents.deleteByPollId(pollId);
		documents.flush();
		for (int index = 0; index < requested.size(); index++) {
			documents.save(PollDocument.create(campusId, pollId, requested.get(index), index));
		}
	}

	public List<Long> getOrderedAssetIds(Long pollId) {
		return documents.findByPollIdOrderByDisplayOrderAscIdAsc(pollId).stream()
			.map(PollDocument::mediaAssetId).toList();
	}

	public Map<Long, List<Long>> getOrderedAssetIdsByPollIds(List<Long> pollIds) {
		if (pollIds.isEmpty()) return Map.of();
		Map<Long, List<Long>> result = new LinkedHashMap<>();
		pollIds.forEach(id -> result.put(id, new ArrayList<>()));
		documents.findByPollIdInOrderByPollIdAscDisplayOrderAscIdAsc(pollIds)
			.forEach(document -> result.get(document.pollId()).add(document.mediaAssetId()));
		return result.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
			entry -> List.copyOf(entry.getValue()), (left, right) -> left, LinkedHashMap::new));
	}
}
