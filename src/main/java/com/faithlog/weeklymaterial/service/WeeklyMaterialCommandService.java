package com.faithlog.weeklymaterial.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialStatus;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAccessPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAttachmentConflictPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialSlotLockPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class WeeklyMaterialCommandService {
	private final WeeklyMaterialRepositoryPort materials;
	private final MediaAssetRepositoryPort assets;
	private final WeeklyMaterialAccessPort access;
	private final WeeklyMaterialAttachmentConflictPort foreignAttachments;
	private final WeeklyMaterialSlotLockPort slotLocks;
	private final WeeklyMaterialFirstPublication firstPublication;

	@Autowired
	public WeeklyMaterialCommandService(WeeklyMaterialRepositoryPort materials, MediaAssetRepositoryPort assets,
		WeeklyMaterialAccessPort access, WeeklyMaterialAttachmentConflictPort foreignAttachments,
		WeeklyMaterialSlotLockPort slotLocks, WeeklyMaterialFirstPublication firstPublication) {
		this.materials = materials;
		this.assets = assets;
		this.access = access;
		this.foreignAttachments = foreignAttachments;
		this.slotLocks = slotLocks;
		this.firstPublication = firstPublication;
	}

	public WeeklyMaterialCommandService(WeeklyMaterialRepositoryPort materials, MediaAssetRepositoryPort assets,
		WeeklyMaterialAccessPort access, WeeklyMaterialAttachmentConflictPort foreignAttachments) {
		this(materials, assets, access, foreignAttachments, campusId -> {}, null);
	}

	@Transactional
	public WeeklyMaterial put(Long campusId, LocalDate weekStartDate, WeeklyMaterialType materialType,
		Long mediaAssetId, Long requesterId) {
		access.requireManager(campusId, requesterId);
		LocalDate week = requireMonday(weekStartDate);
		slotLocks.lockCampus(campusId);
		WeeklyMaterial current = materials.findSlotForUpdate(campusId, week, materialType).orElse(null);
		List<Long> lockIds = current == null || current.mediaAssetId() == null
			? List.of(mediaAssetId)
			: java.util.stream.Stream.of(mediaAssetId, current.mediaAssetId()).distinct().sorted().toList();
		Map<Long, MediaAsset> locked = assets.findByCampusIdAndIdInForUpdate(campusId, lockIds).stream()
			.collect(Collectors.toMap(MediaAsset::id, Function.identity()));
		if (locked.size() != lockIds.size()) throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		MediaAsset incoming = locked.get(mediaAssetId);
		requireAttachable(incoming, requesterId);
		if (!foreignAttachments.findAttachedAssetIds(List.of(mediaAssetId)).isEmpty()) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		}
		List<Long> weeklyConflicts = current == null
			? materials.findAttachedAssetIds(List.of(mediaAssetId))
			: materials.findAttachedAssetIdsExcludingMaterialId(List.of(mediaAssetId), current.id());
		if (!weeklyConflicts.isEmpty()) throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		if (current == null) {
			WeeklyMaterial saved = materials.save(
				WeeklyMaterial.create(campusId, week, materialType, mediaAssetId, requesterId));
			if (firstPublication != null) firstPublication.recordFirstRegistration(saved, true);
			return saved;
		}
		if (current.status() == WeeklyMaterialStatus.DELETED) {
			current.reregister(mediaAssetId, requesterId);
			return current;
		}
		if (!current.mediaAssetId().equals(mediaAssetId)) {
			MediaAsset old = locked.get(current.mediaAssetId());
			if (old == null || old.kind() != MediaAssetKind.PDF || old.status() != MediaAssetStatus.READY) {
				throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
			}
			current.replaceMedia(mediaAssetId, requesterId);
			old.markOrphaned();
		}
		return current;
	}

	@Transactional
	public void delete(Long campusId, LocalDate weekStartDate, WeeklyMaterialType materialType, Long requesterId) {
		access.requireManager(campusId, requesterId);
		slotLocks.lockCampus(campusId);
		WeeklyMaterial current = materials.findSlotForUpdate(campusId, requireMonday(weekStartDate), materialType)
			.filter(material -> material.status() == WeeklyMaterialStatus.ACTIVE)
			.orElseThrow(() -> new BusinessException(ErrorCode.WEEKLY_MATERIAL_NOT_FOUND));
		MediaAsset old = assets.findByCampusIdAndIdInForUpdate(campusId, List.of(current.mediaAssetId())).stream()
			.findFirst().orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ASSET_INVALID));
		if (old.kind() != MediaAssetKind.PDF || old.status() != MediaAssetStatus.READY) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		old.markOrphaned();
		current.delete();
	}

	private static void requireAttachable(MediaAsset asset, Long requesterId) {
		if (asset == null || asset.kind() != MediaAssetKind.PDF || asset.status() != MediaAssetStatus.READY
			|| !asset.ownerUserId().equals(requesterId)) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
	}

	private static LocalDate requireMonday(LocalDate value) {
		try {
			return WeeklyMaterialWeek.requireMonday(value);
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.WEEKLY_MATERIAL_INVALID_WEEK_START_DATE);
		}
	}
}
