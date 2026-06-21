package com.faithlog.prayer.application;

import java.time.LocalDate;
import java.util.List;

public record PrayerWeekBoardResult(
	Long campusId,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	String status,
	long submittedCount,
	long targetMemberCount,
	List<PrayerGroupBoardResult> groups
) {
}
