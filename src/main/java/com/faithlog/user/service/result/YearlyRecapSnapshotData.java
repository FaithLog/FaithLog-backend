package com.faithlog.user.service.result;

import java.util.List;

public record YearlyRecapSnapshotData(
	int recapYear,
	boolean hasRecapData,
	List<CampusJourneyResult> campuses,
	DevotionRecapResult devotion,
	PrayerActivityRecapResult prayerActivity,
	PollActivityRecapResult pollActivity
) {
	public YearlyRecapSnapshotData {
		campuses = List.copyOf(campuses);
	}
}
