package com.faithlog.prayer.controller.dto.response;

import com.faithlog.prayer.service.result.PrayerMemberSubmissionResult;
import java.time.Instant;

public record PrayerMemberSubmissionResponse(
	Long userId,
	String name,
	Long submissionId,
	String content,
	boolean submitted,
	boolean editable,
	int version,
	Instant submittedAt
) {

	public static PrayerMemberSubmissionResponse from(PrayerMemberSubmissionResult result) {
		return new PrayerMemberSubmissionResponse(
			result.userId(),
			result.name(),
			result.submissionId(),
			result.content(),
			result.submitted(),
			result.editable(),
			result.version(),
			result.submittedAt()
		);
	}
}
