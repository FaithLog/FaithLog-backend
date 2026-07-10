package com.faithlog.devotion.service.query;

import java.time.LocalDate;

public record GetMyWeeklyDevotionQuery(
	Long campusId,
	Long requesterId,
	LocalDate weekStartDate
) {
}
