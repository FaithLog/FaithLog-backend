package com.faithlog.devotion.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.faithlog.devotion.application.WeeklyDevotionResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeeklyDevotionResponse(
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
	List<DailyDevotionCheckResponse> dailyChecks
) {

	public static WeeklyDevotionResponse from(WeeklyDevotionResult result) {
		return new WeeklyDevotionResponse(
			result.weeklyRecordId(),
			result.campusId(),
			result.campusName(),
			result.region(),
			result.userId(),
			result.weekStartDate(),
			result.weekEndDate(),
			result.quietTimeCount(),
			result.prayerCount(),
			result.bibleReadingCount(),
			result.saturdayLateMinutes(),
			result.submittedAt(),
			result.dailyChecks().stream()
				.map(DailyDevotionCheckResponse::from)
				.toList()
		);
	}
}
