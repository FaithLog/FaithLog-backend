package com.faithlog.prayer.application;

import java.time.LocalDate;

public record CreatePrayerSeasonCommand(
	Long campusId,
	Long requesterId,
	String name,
	LocalDate startDate
) {
}
