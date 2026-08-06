package com.faithlog.weeklymaterial.infrastructure.repository;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialQueryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRow;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeeklyMaterialRepository
	extends JpaRepository<WeeklyMaterial, Long>, WeeklyMaterialRepositoryPort, WeeklyMaterialQueryPort {
	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select material from WeeklyMaterial material where material.id = :id")
	Optional<WeeklyMaterial> findByIdForUpdate(@Param("id") Long id);

	@Override
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select material from WeeklyMaterial material
		where material.weekStartDate = :weekStartDate
		  and material.materialType = :materialType
		  and ((material.materialType = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE
		        and material.scopeCampusId = :campusId)
		    or (material.materialType <> com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE
		        and material.scopeCampusId is null))
		""")
	Optional<WeeklyMaterial> findSlotForUpdate(@Param("campusId") Long campusId,
		@Param("weekStartDate") LocalDate weekStartDate,
		@Param("materialType") WeeklyMaterialType materialType);

	@Override
	@Query(value = """
		select material.id from weekly_materials material
		where material.week_start_date + INTERVAL '3 months' <= :today
		order by material.week_start_date, material.id
		""", nativeQuery = true)
	List<Long> findDuePhysicalDeletionIds(@Param("today") LocalDate today, Pageable pageable);

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

	@Override
	@Query("""
		select material.mediaAssetId from WeeklyMaterial material
		where material.status = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialStatus.ACTIVE
		  and material.mediaAssetId in :assetIds
		  and ((material.materialType = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE
		        and material.scopeCampusId = :campusId)
		    or material.materialType <> com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE)
		""")
	Set<Long> findActiveAttachedAssetIds(@Param("campusId") Long campusId,
		@Param("assetIds") List<Long> assetIds);

	@Override
	@Query("""
		select new com.faithlog.weeklymaterial.service.port.WeeklyMaterialRow(
			material.id, material.weekStartDate, material.materialType, asset.id,
			asset.originalFileName, asset.outputByteSize, asset.outputSha256, material.updatedAt)
		from WeeklyMaterial material
		join MediaAsset asset on asset.id = material.mediaAssetId and asset.campusId = material.mediaCampusId
		where material.status = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialStatus.ACTIVE
		  and material.weekStartDate in :weekStartDates
		  and ((material.materialType = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE
		        and material.scopeCampusId = :campusId)
		    or material.materialType <> com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE)
		order by material.weekStartDate desc, material.id desc
		""")
	List<WeeklyMaterialRow> findActiveRows(@Param("campusId") Long campusId,
		@Param("weekStartDates") List<LocalDate> weekStartDates);

	@Override
	@Query(value = """
		select distinct material.weekStartDate from WeeklyMaterial material
		where material.status = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialStatus.ACTIVE
		  and material.weekStartDate >= :fromInclusive and material.weekStartDate < :toExclusive
		  and ((material.materialType = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE
		        and material.scopeCampusId = :campusId)
		    or material.materialType <> com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE)
		order by material.weekStartDate desc
		""", countQuery = """
		select count(distinct material.weekStartDate) from WeeklyMaterial material
		where material.status = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialStatus.ACTIVE
		  and material.weekStartDate >= :fromInclusive and material.weekStartDate < :toExclusive
		  and ((material.materialType = com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE
		        and material.scopeCampusId = :campusId)
		    or material.materialType <> com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType.SHEPHERD_GUIDE)
		""")
	Page<LocalDate> findActiveWeekDates(@Param("campusId") Long campusId,
		@Param("fromInclusive") LocalDate fromInclusive,
		@Param("toExclusive") LocalDate toExclusive,
		Pageable pageable);
}
