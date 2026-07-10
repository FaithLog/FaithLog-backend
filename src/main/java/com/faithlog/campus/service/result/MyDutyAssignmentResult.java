package com.faithlog.campus.service.result;

public record MyDutyAssignmentResult(
	Long userId,
	Long campusId,
	String dutyType,
	boolean active
) {
}
