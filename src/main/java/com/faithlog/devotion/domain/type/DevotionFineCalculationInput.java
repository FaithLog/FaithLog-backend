package com.faithlog.devotion.domain.type;

public record DevotionFineCalculationInput(
	int quietTimeCount,
	int prayerCount,
	int bibleReadingCount,
	int saturdayLateMinutes
) {
}
