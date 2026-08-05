package com.faithlog.weeklymaterial.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialSlotLockPort;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class WeeklyMaterialRetentionService {
	private final WeeklyMaterialRepositoryPort materials;
	private final MediaAssetRepositoryPort assets;
	private final WeeklyMaterialFirstPublication firstPublication;
	private final WeeklyMaterialSlotLockPort slotLocks;

	@Autowired
	public WeeklyMaterialRetentionService(WeeklyMaterialRepositoryPort materials, MediaAssetRepositoryPort assets,
		WeeklyMaterialFirstPublication firstPublication, WeeklyMaterialSlotLockPort slotLocks) {
		this.materials = materials;
		this.assets = assets;
		this.firstPublication = firstPublication;
		this.slotLocks = slotLocks;
	}

	public WeeklyMaterialRetentionService(WeeklyMaterialRepositoryPort materials, MediaAssetRepositoryPort assets,
		WeeklyMaterialFirstPublication firstPublication) {
		this(materials, assets, firstPublication, () -> {});
	}

	@Transactional
	public boolean deleteIfDue(Long weeklyMaterialId, LocalDate today) {
		slotLocks.lockGlobal();
		WeeklyMaterial material = materials.findByIdForUpdate(weeklyMaterialId).orElse(null);
		if (material == null || material.weekStartDate().plusMonths(3).isAfter(today)) return false;
		firstPublication.suppressPending(material);

		if (material.mediaAssetId() != null) {
			List<MediaAsset> locked = assets.findByIdInForUpdate(List.of(material.mediaAssetId()));
			if (locked.size() != 1) throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
			MediaAsset asset = locked.getFirst();
			if (!asset.campusId().equals(material.mediaCampusId())
				|| asset.kind() != MediaAssetKind.PDF || asset.status() != MediaAssetStatus.READY) {
				throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
			}
			asset.markOrphaned();
		}
		materials.delete(material);
		return true;
	}
}
