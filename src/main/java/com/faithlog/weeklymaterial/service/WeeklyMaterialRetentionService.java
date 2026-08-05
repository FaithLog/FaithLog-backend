package com.faithlog.weeklymaterial.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyMaterialRetentionService {
	private final WeeklyMaterialRepositoryPort materials;
	private final MediaAssetRepositoryPort assets;

	public WeeklyMaterialRetentionService(WeeklyMaterialRepositoryPort materials, MediaAssetRepositoryPort assets) {
		this.materials = materials;
		this.assets = assets;
	}

	@Transactional
	public boolean deleteIfDue(Long weeklyMaterialId, LocalDate today) {
		WeeklyMaterial material = materials.findByIdForUpdate(weeklyMaterialId).orElse(null);
		if (material == null || material.weekStartDate().plusMonths(3).isAfter(today)) return false;

		if (material.mediaAssetId() != null) {
			List<MediaAsset> locked = assets.findByCampusIdAndIdInForUpdate(
				material.campusId(), List.of(material.mediaAssetId()));
			if (locked.size() != 1) throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
			MediaAsset asset = locked.getFirst();
			if (asset.kind() != MediaAssetKind.PDF || asset.status() != MediaAssetStatus.READY) {
				throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
			}
			asset.markOrphaned();
		}
		materials.delete(material);
		return true;
	}
}
