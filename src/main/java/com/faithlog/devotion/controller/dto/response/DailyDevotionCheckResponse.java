package com.faithlog.devotion.controller.dto.response;

import com.faithlog.devotion.service.result.DailyDevotionCheckResult;
import java.time.LocalDate;

public record DailyDevotionCheckResponse(
	Long id,
	LocalDate recordDate,
	boolean quietTimeChecked,
	boolean prayerChecked,
	boolean bibleReadingChecked
) {

	public static DailyDevotionCheckResponse from(DailyDevotionCheckResult result) {
		return new DailyDevotionCheckResponse(
			result.id(),
			result.recordDate(),
			result.quietTimeChecked(),
			result.prayerChecked(),
			result.bibleReadingChecked()
		);
	}
}
