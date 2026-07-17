package com.faithlog.prayer.controller.dto.response;

import com.faithlog.prayer.service.result.PrayerSeasonResult;
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
