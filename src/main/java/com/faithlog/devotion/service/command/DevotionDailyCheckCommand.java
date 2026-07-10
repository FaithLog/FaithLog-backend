package com.faithlog.devotion.service.command;

import java.time.LocalDate;

public record DevotionDailyCheckCommand(
	LocalDate recordDate,
	boolean quietTimeChecked,
	boolean prayerChecked,
	boolean bibleReadingChecked
) {
}
