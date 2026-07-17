package com.faithlog.prayer.service.result;

public record PrayerGroupMemberResult(
	Long userId,
	String name,
	String email
) {
}
