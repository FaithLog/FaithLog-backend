package com.faithlog.prayer.service.command;

import java.time.LocalDate;
import java.util.List;

public record SavePrayerSubmissionsCommand(
	Long campusId,
	LocalDate weekStartDate,
	Long requesterId,
	List<PrayerSubmissionCommand> submissions
) {
}
