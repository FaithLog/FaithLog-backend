package com.faithlog.devotion.service.result;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
			completeWeeklyDailyChecks(weeklyRecord.weekStartDate(), dailyChecks)
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

	private static List<DailyDevotionCheckResult> completeWeeklyDailyChecks(
		LocalDate weekStartDate,
		List<DevotionDailyCheck> dailyChecks
	) {
		Map<LocalDate, DevotionDailyCheck> dailyChecksByDate = dailyChecks.stream()
			.collect(Collectors.toMap(
				DevotionDailyCheck::recordDate,
				Function.identity(),
				(first, second) -> second
			));
		return LongStream.rangeClosed(0, 6)
			.mapToObj(weekStartDate::plusDays)
			.map(recordDate -> {
				DevotionDailyCheck dailyCheck = dailyChecksByDate.get(recordDate);
				if (dailyCheck == null) {
					return DailyDevotionCheckResult.unchecked(recordDate);
				}
				return DailyDevotionCheckResult.from(dailyCheck);
			})
			.toList();
	}
}
