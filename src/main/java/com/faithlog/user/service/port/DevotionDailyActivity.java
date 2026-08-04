package com.faithlog.user.service.port;

import java.time.LocalDate;

public record DevotionDailyActivity(
	LocalDate recordDate,
	boolean quietTimeChecked,
	boolean bibleReadingChecked,
	boolean prayerChecked
) {
	public boolean allCompleted() {
		return quietTimeChecked && bibleReadingChecked && prayerChecked;
	}

	public int completedCount() {
		return (quietTimeChecked ? 1 : 0)
			+ (bibleReadingChecked ? 1 : 0)
			+ (prayerChecked ? 1 : 0);
	}
}
