package com.faithlog.admin.application;

public record AddCampusMemberCommand(
	Long requesterId,
	Long campusId,
	Long userId
) {
}
