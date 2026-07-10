package com.faithlog.campus.infrastructure.repository;

import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.type.DutyType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusDutyAssignmentRepository extends
	JpaRepository<CampusDutyAssignment, Long>,
	CampusDutyAssignmentRepositoryPort {

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndIsActiveTrue(Long campusId, DutyType dutyType);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndId(Long campusId, DutyType dutyType, Long id);

	List<CampusDutyAssignment> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);
}
