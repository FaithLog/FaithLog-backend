package com.faithlog.devotion.application;

import com.faithlog.campus.domain.Campus;
import com.faithlog.devotion.domain.DevotionDailyCheck;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

public record WeeklyDevotionResult(
	Long weeklyRecordId,
	Long campusId,
	String campusName,
	String region,
	Long userId,
	LocalDate weekStartDate,
	LocalDate weekEndDate,
	int quietTimeCount,
	int prayerCount,
	int bibleReadingCount,
	int saturdayLateMinutes,
	Instant submittedAt,
	List<DailyDevotionCheckResult> dailyChecks
) {

	public static WeeklyDevotionResult of(
		WeeklyDevotionRecord weeklyRecord,
		Campus campus,
		List<DevotionDailyCheck> dailyChecks
	) {
		return new WeeklyDevotionResult(
			weeklyRecord.id(),
			campus.id(),
			campus.name(),
			campus.region(),
			weeklyRecord.userId(),
			weeklyRecord.weekStartDate(),
			weeklyRecord.weekEndDate(),
			weeklyRecord.quietTimeCount(),
			weeklyRecord.prayerCount(),
			weeklyRecord.bibleReadingCount(),
			weeklyRecord.saturdayLateMinutes(),
			weeklyRecord.submittedAt(),
			dailyChecks.stream()
				.map(DailyDevotionCheckResult::from)
				.toList()
		);
	}

	public static WeeklyDevotionResult defaultOf(Campus campus, Long userId, LocalDate weekStartDate) {
		return new WeeklyDevotionResult(
			null,
			campus.id(),
			campus.name(),
			campus.region(),
			userId,
			weekStartDate,
			weekStartDate.plusDays(6),
			0,
			0,
			0,
			0,
			null,
			LongStream.rangeClosed(0, 6)
				.mapToObj(weekStartDate::plusDays)
				.map(DailyDevotionCheckResult::unchecked)
				.toList()
		);
	}
}
