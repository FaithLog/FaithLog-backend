package com.faithlog.devotion.presentation.dto;

import com.faithlog.devotion.application.MyMonthlyDevotionSummaryResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MyMonthlyDevotionSummaryResponse(
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

	public static MyMonthlyDevotionSummaryResponse from(MyMonthlyDevotionSummaryResult result) {
		return new MyMonthlyDevotionSummaryResponse(
			result.campusId(),
			result.campusName(),
			result.region(),
			result.userId(),
			result.name(),
			result.year(),
			result.month(),
			Devotion.from(result.devotion()),
			result.weeklyRecords().stream().map(WeeklyRecord::from).toList()
		);
	}

	public record Devotion(
		int quietTimeCount,
		int prayerCount,
		int bibleReadingCount,
		int saturdayLateMinutes
	) {

		static Devotion from(MyMonthlyDevotionSummaryResult.Devotion result) {
			return new Devotion(
				result.quietTimeCount(),
				result.prayerCount(),
				result.bibleReadingCount(),
				result.saturdayLateMinutes()
			);
		}
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

		static WeeklyRecord from(MyMonthlyDevotionSummaryResult.WeeklyRecord result) {
			return new WeeklyRecord(
				result.weeklyRecordId(),
				result.weekStartDate(),
				result.weekEndDate(),
				result.quietTimeCount(),
				result.prayerCount(),
				result.bibleReadingCount(),
				result.saturdayLateMinutes(),
				result.submittedAt()
			);
		}
	}
}
