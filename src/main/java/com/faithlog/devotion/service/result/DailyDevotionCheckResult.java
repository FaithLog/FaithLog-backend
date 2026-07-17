package com.faithlog.devotion.service.result;

import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
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

	public static DailyDevotionCheckResult unchecked(LocalDate recordDate) {
		return new DailyDevotionCheckResult(
			null,
			recordDate,
			false,
			false,
			false
		);
	}
}
