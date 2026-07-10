package com.faithlog.admin.service.command;

public record AddCampusMemberCommand(
	Long requesterId,
	Long campusId,
	Long userId
) {
}
