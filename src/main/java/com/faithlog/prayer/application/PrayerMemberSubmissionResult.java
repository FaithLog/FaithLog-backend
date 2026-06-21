package com.faithlog.prayer.application;

import java.time.Instant;

public record PrayerMemberSubmissionResult(
	Long userId,
	String name,
	Long submissionId,
	String content,
	int version,
	Instant submittedAt
) {
}
