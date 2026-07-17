package com.faithlog.prayer.service.command;

import java.time.LocalDate;

public record CreatePrayerSeasonCommand(
	Long campusId,
	Long requesterId,
	String name,
	LocalDate startDate
) {
}
