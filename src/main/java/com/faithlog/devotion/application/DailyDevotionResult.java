package com.faithlog.devotion.application;

import com.faithlog.devotion.domain.DevotionDailyCheck;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import java.time.Instant;
import java.time.LocalDate;

public record DailyDevotionResult(
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

	public static DailyDevotionResult of(WeeklyDevotionRecord weeklyRecord, DevotionDailyCheck dailyCheck) {
		return new DailyDevotionResult(
			weeklyRecord.id(),
			dailyCheck.recordDate(),
			dailyCheck.quietTimeChecked(),
			dailyCheck.prayerChecked(),
			dailyCheck.bibleReadingChecked(),
			weeklyRecord.quietTimeCount(),
			weeklyRecord.prayerCount(),
			weeklyRecord.bibleReadingCount(),
			weeklyRecord.submittedAt()
		);
	}
}
