package com.faithlog.prayer.service.command;

import java.time.LocalDate;

public record ClosePrayerSeasonCommand(
	Long seasonId,
	Long requesterId,
	LocalDate endDate
) {
}
