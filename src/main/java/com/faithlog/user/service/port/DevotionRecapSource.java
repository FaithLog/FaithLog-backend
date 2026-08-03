package com.faithlog.user.service.port;

import java.util.List;

public record DevotionRecapSource(
	List<DevotionDailyActivity> dailyActivities,
	int submittedWeekCount
) {
	public DevotionRecapSource {
		dailyActivities = List.copyOf(dailyActivities);
	}
}
