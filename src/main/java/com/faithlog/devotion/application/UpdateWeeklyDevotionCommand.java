package com.faithlog.devotion.application;

import java.time.LocalDate;
import java.util.List;

public record UpdateWeeklyDevotionCommand(
	Long campusId,
	Long requesterId,
	LocalDate weekStartDate,
	List<DevotionDailyCheckCommand> dailyChecks,
	int saturdayLateMinutes,
	boolean submit
) {
}
