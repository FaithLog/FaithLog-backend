package com.faithlog.campus.service.command;

public record AssignMealDutyCommand(
	Long campusId,
	Long requesterId,
	Long userId
) {
}
