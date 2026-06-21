package com.faithlog.prayer.application;

public record PrayerSubmissionCommand(
	Long userId,
	String content,
	int version
) {
}
