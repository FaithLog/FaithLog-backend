package com.faithlog.prayer.application;

import java.time.LocalDate;
import java.util.List;

public record PrayerWeekBoardResult(
	Long campusId,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	PrayerSeasonResult currentSeason,
	Long myGroupId,
	String status,
	long submittedCount,
	long targetMemberCount,
	List<PrayerGroupBoardResult> groups
) {

	public static PrayerWeekBoardResult empty(Long campusId, LocalDate weekStartDate) {
		return new PrayerWeekBoardResult(
			campusId,
			weekStartDate,
			weekStartDate.plusDays(6),
			null,
			null,
			"OPEN",
			0,
			0,
			List.of()
		);
	}
}
