package com.faithlog.devotion.application;

import java.time.LocalDate;

public record GetMyWeeklyDevotionQuery(
	Long campusId,
	Long requesterId,
	LocalDate weekStartDate
) {
}
