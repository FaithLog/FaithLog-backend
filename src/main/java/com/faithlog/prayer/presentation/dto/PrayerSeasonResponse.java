package com.faithlog.prayer.presentation.dto;

import com.faithlog.prayer.application.PrayerSeasonResult;
import java.time.LocalDate;

public record PrayerSeasonResponse(
	Long seasonId,
	Long campusId,
	String name,
	LocalDate startDate,
	LocalDate endDate,
	String status
) {

	public static PrayerSeasonResponse from(PrayerSeasonResult result) {
		return new PrayerSeasonResponse(
			result.seasonId(),
			result.campusId(),
			result.name(),
			result.startDate(),
			result.endDate(),
			result.status()
		);
	}
}
