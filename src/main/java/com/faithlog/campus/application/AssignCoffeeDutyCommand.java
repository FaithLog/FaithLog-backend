package com.faithlog.campus.application;

public record AssignCoffeeDutyCommand(
	Long campusId,
	Long requesterId,
	Long userId
) {
}
