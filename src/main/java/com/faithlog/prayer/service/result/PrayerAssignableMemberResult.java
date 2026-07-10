package com.faithlog.prayer.service.result;

public record PrayerAssignableMemberResult(
	Long userId,
	String name,
	String email,
	Long assignedGroupId,
	String assignedGroupName,
	boolean assignable
) {
}
