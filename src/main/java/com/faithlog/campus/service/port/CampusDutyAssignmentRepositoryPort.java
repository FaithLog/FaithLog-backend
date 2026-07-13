package com.faithlog.campus.service.port;

import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.type.DutyType;
import java.util.List;
import java.util.Optional;

public interface CampusDutyAssignmentRepositoryPort {

	CampusDutyAssignment save(CampusDutyAssignment assignment);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndIsActiveTrue(Long campusId, DutyType dutyType);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(
		Long campusId,
		DutyType dutyType,
		Long userId
	);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndId(Long campusId, DutyType dutyType, Long id);

	List<CampusDutyAssignment> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);
}
