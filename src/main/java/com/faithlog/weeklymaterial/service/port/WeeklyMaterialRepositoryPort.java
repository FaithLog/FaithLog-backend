package com.faithlog.weeklymaterial.service.port;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyMaterialRepositoryPort {
	WeeklyMaterial save(WeeklyMaterial material);
	Optional<WeeklyMaterial> findSlotForUpdate(Long campusId, LocalDate weekStartDate, WeeklyMaterialType materialType);
	List<Long> findAttachedAssetIds(List<Long> assetIds);
	List<Long> findAttachedAssetIdsExcludingMaterialId(List<Long> assetIds, Long materialId);
}
