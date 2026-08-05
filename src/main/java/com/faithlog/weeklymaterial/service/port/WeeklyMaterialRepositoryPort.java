package com.faithlog.weeklymaterial.service.port;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;

public interface WeeklyMaterialRepositoryPort {
	WeeklyMaterial save(WeeklyMaterial material);
	Optional<WeeklyMaterial> findByIdForUpdate(Long id);
	Optional<WeeklyMaterial> findSlotForUpdate(LocalDate weekStartDate, WeeklyMaterialType materialType);
	List<Long> findDuePhysicalDeletionIds(LocalDate today, Pageable pageable);
	List<Long> findAttachedAssetIds(List<Long> assetIds);
	List<Long> findAttachedAssetIdsExcludingMaterialId(List<Long> assetIds, Long materialId);
	Set<Long> findActiveAttachedAssetIds(List<Long> assetIds);
	void delete(WeeklyMaterial material);
}
