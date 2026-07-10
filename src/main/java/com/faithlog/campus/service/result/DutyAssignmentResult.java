package com.faithlog.campus.service.result;

import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import java.time.Instant;

public record DutyAssignmentResult(
	Long assignmentId,
	Long campusId,
	Long userId,
	String name,
	String email,
	String dutyType,
	boolean active,
	Instant assignedAt
) {

	public static DutyAssignmentResult of(CampusDutyAssignment assignment, CampusUserLookupResult user) {
		return new DutyAssignmentResult(
			assignment.id(),
			assignment.campusId(),
			assignment.userId(),
			user.name(),
			user.email(),
			assignment.dutyType().name(),
			assignment.isActive(),
			assignment.assignedAt()
		);
	}
}
