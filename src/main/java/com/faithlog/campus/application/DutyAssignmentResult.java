package com.faithlog.campus.application;

import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.domain.CampusDutyAssignment;
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
