package com.faithlog.devotion.application;

import java.time.LocalDate;

public record DevotionDailyCheckCommand(
	LocalDate recordDate,
	boolean quietTimeChecked,
	boolean prayerChecked,
	boolean bibleReadingChecked
) {
}
