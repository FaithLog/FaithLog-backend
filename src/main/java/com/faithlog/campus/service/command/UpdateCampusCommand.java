package com.faithlog.campus.service.command;

public record UpdateCampusCommand(
	Long requesterId,
	Long campusId,
	String name,
	String region,
	String description,
	Boolean isActive
) {
}
