package com.faithlog.prayer.application;

import java.time.LocalDate;

public record SaveMyPrayerSubmissionCommand(
	Long campusId,
	LocalDate weekStartDate,
	Long requesterId,
	String content
) {
}
