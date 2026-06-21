package com.faithlog.prayer.application;

import java.time.LocalDate;

public record ClosePrayerSeasonCommand(
	Long seasonId,
	Long requesterId,
	LocalDate endDate
) {
}
