package com.faithlog.devotion.application;

import java.time.LocalDate;

public record UpdateDailyDevotionCommand(
	Long campusId,
	Long requesterId,
	LocalDate recordDate,
	boolean quietTimeChecked,
	boolean prayerChecked,
	boolean bibleReadingChecked
) {
}
