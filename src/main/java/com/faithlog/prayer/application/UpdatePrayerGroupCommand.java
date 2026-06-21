package com.faithlog.prayer.application;

public record UpdatePrayerGroupCommand(
	Long groupId,
	Long requesterId,
	String name,
	Integer sortOrder,
	Boolean isActive
) {
}
