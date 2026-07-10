package com.faithlog.campus.service.command;

public record AssignCoffeeDutyCommand(
	Long campusId,
	Long requesterId,
	Long userId
) {
}
