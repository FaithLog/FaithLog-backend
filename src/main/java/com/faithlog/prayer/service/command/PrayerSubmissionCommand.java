package com.faithlog.prayer.service.command;

public record PrayerSubmissionCommand(
	Long userId,
	String content,
	int version
) {
}
