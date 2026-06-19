package com.faithlog.devotion.application;

public record GetMyMonthlyDevotionSummaryQuery(
	Long campusId,
	Long requesterId,
	int year,
	int month
) {
}
