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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select assignment
		from CampusDutyAssignment assignment
		where assignment.campusId = :campusId
		  and assignment.userId = :userId
		  and assignment.isActive = true
		order by assignment.id asc
		""")
	List<CampusDutyAssignment> findActiveByCampusIdAndUserIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("userId") Long userId
	);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndId(Long campusId, DutyType dutyType, Long id);

	@Query("""
		select assignment
		from CampusDutyAssignment assignment
		where assignment.campusId = :campusId
		  and assignment.isActive = true
		  and exists (
			select member.id
			from CampusMember member
			where member.campusId = assignment.campusId
			  and member.userId = assignment.userId
			  and member.status = com.faithlog.campus.domain.type.CampusMemberStatus.ACTIVE
		  )
		order by assignment.id asc
		""")
	List<CampusDutyAssignment> findActiveWithActiveMemberByCampusIdOrderByIdAsc(
		@Param("campusId") Long campusId
	);
}
