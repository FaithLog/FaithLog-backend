package com.faithlog.prayer.controller.dto.response;

import com.faithlog.prayer.service.result.PrayerWeekBoardResult;
import java.time.LocalDate;
import java.util.List;

public record PrayerWeekBoardResponse(
	Long campusId,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	PrayerSeasonResponse currentSeason,
	Long myGroupId,
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
			result.currentSeason() == null ? null : PrayerSeasonResponse.from(result.currentSeason()),
			result.myGroupId(),
			result.status(),
			result.submittedCount(),
			result.targetMemberCount(),
			result.groups().stream().map(PrayerGroupBoardResponse::from).toList()
		);
	}
}
