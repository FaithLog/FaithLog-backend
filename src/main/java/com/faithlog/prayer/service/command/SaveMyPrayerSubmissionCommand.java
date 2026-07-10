package com.faithlog.prayer.service.command;

import java.time.LocalDate;

public record SaveMyPrayerSubmissionCommand(
	Long campusId,
	LocalDate weekStartDate,
	Long requesterId,
	String content
) {
}
