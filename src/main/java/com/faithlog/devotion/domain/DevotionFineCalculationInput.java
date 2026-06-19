package com.faithlog.devotion.domain;

public record DevotionFineCalculationInput(
	int quietTimeCount,
	int prayerCount,
	int bibleReadingCount,
	int saturdayLateMinutes
) {
}
