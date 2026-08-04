package com.faithlog.user.service.port;

public record PrayerRecapAggregate(
	int submittedWeekCount,
	int participatedSeasonCount
) {
}
