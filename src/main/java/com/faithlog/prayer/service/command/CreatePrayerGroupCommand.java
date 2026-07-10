package com.faithlog.prayer.service.command;

public record CreatePrayerGroupCommand(
	Long seasonId,
	Long requesterId,
	String name,
	int sortOrder
) {
}
