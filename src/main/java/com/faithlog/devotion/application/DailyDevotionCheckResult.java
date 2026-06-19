package com.faithlog.devotion.application;

import com.faithlog.devotion.domain.DevotionDailyCheck;
import java.time.LocalDate;

public record DailyDevotionCheckResult(
	Long id,
	LocalDate recordDate,
	boolean quietTimeChecked,
	boolean prayerChecked,
	boolean bibleReadingChecked
) {

	public static DailyDevotionCheckResult from(DevotionDailyCheck dailyCheck) {
		return new DailyDevotionCheckResult(
			dailyCheck.id(),
			dailyCheck.recordDate(),
			dailyCheck.quietTimeChecked(),
			dailyCheck.prayerChecked(),
			dailyCheck.bibleReadingChecked()
		);
	}
}
