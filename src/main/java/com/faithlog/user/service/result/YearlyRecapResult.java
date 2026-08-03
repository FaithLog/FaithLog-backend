package com.faithlog.user.service.result;

import java.util.List;

public record YearlyRecapResult(
	int recapYear,
	boolean hasRecapData,
	YearlyRecapPresentationResult presentation,
	List<CampusJourneyResult> campuses,
	DevotionRecapResult devotion,
	PrayerActivityRecapResult prayerActivity,
	PollActivityRecapResult pollActivity
) {
	public YearlyRecapResult {
		campuses = List.copyOf(campuses);
	}
}
