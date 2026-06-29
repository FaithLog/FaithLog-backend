package com.faithlog.campus.application;

public record MyDutyAssignmentResult(
	Long userId,
	Long campusId,
	String dutyType,
	boolean active
) {
}
