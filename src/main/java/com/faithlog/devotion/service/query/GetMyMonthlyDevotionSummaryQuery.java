package com.faithlog.devotion.service.query;

public record GetMyMonthlyDevotionSummaryQuery(
	Long campusId,
	Long requesterId,
	int year,
	int month
) {
}
