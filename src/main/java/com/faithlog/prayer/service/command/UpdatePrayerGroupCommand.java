package com.faithlog.prayer.service.command;

public record UpdatePrayerGroupCommand(
	Long groupId,
	Long requesterId,
	String name,
	Integer sortOrder,
	Boolean isActive
) {
}
