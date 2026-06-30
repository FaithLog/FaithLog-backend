package com.faithlog.prayer.application;

public record PrayerGroupMemberResult(
	Long userId,
	String name,
	String email
) {
}
