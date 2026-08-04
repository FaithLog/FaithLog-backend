package com.faithlog.announcement.service.command;

public record CreateAnnouncementCategoryCommand(
	Long campusId,
	Long requesterId,
	String name,
	String color,
	int displayOrder
) {
}
