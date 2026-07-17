package com.faithlog.devotion.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.faithlog.devotion.service.result.DailyDevotionResult;
import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyDevotionResponse(
	Long weeklyRecordId,
	LocalDate recordDate,
	boolean quietTimeChecked,
	boolean prayerChecked,
	boolean bibleReadingChecked,
	int quietTimeCount,
	int prayerCount,
	int bibleReadingCount,
	Instant submittedAt
) {

	public static DailyDevotionResponse from(DailyDevotionResult result) {
		return new DailyDevotionResponse(
			result.weeklyRecordId(),
			result.recordDate(),
			result.quietTimeChecked(),
			result.prayerChecked(),
			result.bibleReadingChecked(),
			result.quietTimeCount(),
			result.prayerCount(),
			result.bibleReadingCount(),
			result.submittedAt()
		);
	}
}
