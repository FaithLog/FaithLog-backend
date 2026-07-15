package com.faithlog.campus.service.port;

import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.type.DutyType;
import java.util.List;
import java.util.Optional;

public interface CampusDutyAssignmentRepositoryPort {

	CampusDutyAssignment save(CampusDutyAssignment assignment);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(
		Long campusId,
		DutyType dutyType,
		Long userId
	);

	Optional<CampusDutyAssignment> findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
		Long campusId,
		DutyType dutyType,
		Long userId
	);

	List<CampusDutyAssignment> findActiveByCampusIdAndUserIdForUpdate(Long campusId, Long userId);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndId(Long campusId, DutyType dutyType, Long id);

	List<CampusDutyAssignment> findActiveWithActiveMemberByCampusIdOrderByIdAsc(Long campusId);

	List<CampusDutyAssignment> findActiveWithInactiveMemberByCampusIdOrderByIdAsc(Long campusId);
}
