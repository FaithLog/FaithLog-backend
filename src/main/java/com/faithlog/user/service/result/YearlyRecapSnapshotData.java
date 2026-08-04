package com.faithlog.user.service.result;

import java.util.List;

public record YearlyRecapSnapshotData(
	int recapYear,
	boolean hasRecapData,
	List<CampusJourneyResult> campuses,
	DevotionRecapResult devotion,
	PrayerActivityRecapResult prayerActivity,
	CommentActivityRecapResult commentActivity,
	PenaltySummaryRecapResult penaltySummary
) {
	public YearlyRecapSnapshotData {
		campuses = List.copyOf(campuses);
	}
}
