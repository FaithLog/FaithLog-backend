package com.faithlog.prayer.application;

public record PrayerAssignableMemberResult(
	Long userId,
	String name,
	String email,
	Long assignedGroupId,
	String assignedGroupName,
	boolean assignable
) {
}
