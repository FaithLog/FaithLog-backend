package com.faithlog.prayer.application;

public record CreatePrayerGroupCommand(
	Long seasonId,
	Long requesterId,
	String name,
	int sortOrder
) {
}
