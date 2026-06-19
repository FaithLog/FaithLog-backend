package com.faithlog.campus.application;

public record UpdateCampusCommand(
	Long requesterId,
	Long campusId,
	String name,
	String region,
	String description,
	Boolean isActive
) {
}
