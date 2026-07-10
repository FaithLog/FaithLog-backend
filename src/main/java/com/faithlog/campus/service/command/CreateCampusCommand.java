package com.faithlog.campus.service.command;

public record CreateCampusCommand(
	Long requesterId,
	String name,
	String region,
	String description
) {
}
