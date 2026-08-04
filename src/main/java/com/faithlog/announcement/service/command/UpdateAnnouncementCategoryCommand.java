package com.faithlog.announcement.service.command;

public record UpdateAnnouncementCategoryCommand(
	Long campusId,
	Long categoryId,
	Long requesterId,
	String name,
	String color,
	int displayOrder
) {
}
