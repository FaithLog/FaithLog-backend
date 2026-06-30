package com.faithlog.prayer.application;

import java.time.Instant;

public record PrayerMemberSubmissionResult(
	Long userId,
	String name,
	Long submissionId,
	String content,
	boolean submitted,
	boolean editable,
	int version,
	Instant submittedAt
) {
}
