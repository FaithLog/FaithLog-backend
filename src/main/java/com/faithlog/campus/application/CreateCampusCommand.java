package com.faithlog.campus.application;

public record CreateCampusCommand(
	Long requesterId,
	String name,
	String region,
	String description
) {
}
