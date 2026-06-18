package com.faithlog.campus.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.faithlog.campus.application.DutyAssignmentResult;
import java.time.Instant;

public record DutyAssignmentResponse(
	Long assignmentId,
	Long campusId,
	Long userId,
	String name,
	String email,
	String dutyType,
	@JsonProperty("isActive") boolean active,
	Instant assignedAt
) {

	public static DutyAssignmentResponse from(DutyAssignmentResult result) {
		return new DutyAssignmentResponse(
			result.assignmentId(),
			result.campusId(),
			result.userId(),
			result.name(),
			result.email(),
			result.dutyType(),
			result.active(),
			result.assignedAt()
		);
	}
}
