package com.faithlog.user.service.result;

public record PollActivityRecapResult(
	int participatedCount,
	int wedServicePollCount,
	int saturdayLeaderPollCount,
	int coffeePollCount,
	int mealPollCount,
	int customPollCount,
	int commentCount
) {
}
