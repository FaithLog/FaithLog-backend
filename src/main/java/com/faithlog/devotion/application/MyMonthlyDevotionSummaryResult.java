package com.faithlog.devotion.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MyMonthlyDevotionSummaryResult(
	Long campusId,
	String campusName,
	String region,
	Long userId,
	String name,
	int year,
	int month,
	Devotion devotion,
	List<WeeklyRecord> weeklyRecords
) {

	public record Devotion(
		int quietTimeCount,
		int prayerCount,
		int bibleReadingCount,
		int saturdayLateMinutes
	) {
	}

	public record WeeklyRecord(
		Long weeklyRecordId,
		LocalDate weekStartDate,
		LocalDate weekEndDate,
		int quietTimeCount,
		int prayerCount,
		int bibleReadingCount,
		int saturdayLateMinutes,
		Instant submittedAt
	) {
	}
}
