package com.faithlog.prayer.presentation.dto;

import com.faithlog.prayer.application.PrayerWeekBoardResult;
import java.time.LocalDate;
import java.util.List;

public record PrayerWeekBoardResponse(
	Long campusId,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	String status,
	long submittedCount,
	long targetMemberCount,
	List<PrayerGroupBoardResponse> groups
) {

	public static PrayerWeekBoardResponse from(PrayerWeekBoardResult result) {
		return new PrayerWeekBoardResponse(
			result.campusId(),
			result.weekStartDate(),
			result.weekEndDate(),
			result.status(),
			result.submittedCount(),
			result.targetMemberCount(),
			result.groups().stream().map(PrayerGroupBoardResponse::from).toList()
		);
	}
}
