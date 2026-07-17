package com.faithlog.prayer.service.result;

import com.faithlog.prayer.domain.entity.PrayerSeason;
import java.time.LocalDate;

public record PrayerSeasonResult(
	Long seasonId,
	Long campusId,
	String name,
	LocalDate startDate,
	LocalDate endDate,
	String status
) {

	public static PrayerSeasonResult from(PrayerSeason season) {
		return new PrayerSeasonResult(
			season.id(),
			season.campusId(),
			season.name(),
			season.startDate(),
			season.endDate(),
			season.status().name()
		);
	}
}
