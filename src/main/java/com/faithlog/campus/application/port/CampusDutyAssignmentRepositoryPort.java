package com.faithlog.campus.application.port;

import com.faithlog.campus.domain.CampusDutyAssignment;
import com.faithlog.campus.domain.DutyType;
import java.util.List;
import java.util.Optional;

public interface CampusDutyAssignmentRepositoryPort {

	CampusDutyAssignment save(CampusDutyAssignment assignment);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndIsActiveTrue(Long campusId, DutyType dutyType);

	Optional<CampusDutyAssignment> findByCampusIdAndDutyTypeAndId(Long campusId, DutyType dutyType, Long id);

	List<CampusDutyAssignment> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);
}
