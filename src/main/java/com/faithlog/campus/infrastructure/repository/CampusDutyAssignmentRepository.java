package com.faithlog.campus.infrastructure.repository;

import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.type.DutyType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CampusDutyAssignmentRepository extends
	JpaRepository<CampusDutyAssignment, Long>,
	CampusDutyAssignmentRepositoryPort {

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(
		Long campusId,
		DutyType dutyType,
		Long userId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select assignment
		from CampusDutyAssignment assignment
		where assignment.campusId = :campusId
		  and assignment.dutyType = :dutyType
		  and assignment.userId = :userId
		  and assignment.isActive = true
		""")
	Optional<CampusDutyAssignment> findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("dutyType") DutyType dutyType,
		@Param("userId") Long userId
	);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndId(Long campusId, DutyType dutyType, Long id);

	List<CampusDutyAssignment> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);
}
