package com.faithlog.user.service.result;

public record DevotionRecapResult(
	int quietTimeCount,
	int bibleReadingCount,
	int prayerCount,
	int allCompletedDayCount,
	int submittedWeekCount,
	int longestStreakDays,
	Integer mostActiveMonth
) {
}
