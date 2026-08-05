package com.faithlog.weeklymaterial.infrastructure.repository;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyMaterialRepository
	extends JpaRepository<WeeklyMaterial, Long>, WeeklyMaterialRepositoryPort {

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select material from WeeklyMaterial material
		where material.campusId = :campusId
		  and material.weekStartDate = :weekStartDate
		  and material.materialType = :materialType
		""")
	Optional<WeeklyMaterial> findSlotForUpdate(@Param("campusId") Long campusId,
		@Param("weekStartDate") LocalDate weekStartDate, @Param("materialType") WeeklyMaterialType materialType);

	@Override
	@Query("select material.mediaAssetId from WeeklyMaterial material where material.mediaAssetId in :assetIds")
	List<Long> findAttachedAssetIds(@Param("assetIds") List<Long> assetIds);

	@Override
	@Query("""
		select material.mediaAssetId from WeeklyMaterial material
		where material.id <> :materialId and material.mediaAssetId in :assetIds
		""")
	List<Long> findAttachedAssetIdsExcludingMaterialId(
		@Param("assetIds") List<Long> assetIds, @Param("materialId") Long materialId);
}
