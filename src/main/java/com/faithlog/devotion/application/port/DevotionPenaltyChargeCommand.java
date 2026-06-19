package com.faithlog.devotion.application.port;

import java.time.LocalDate;

public record DevotionPenaltyChargeCommand(
	Long campusId,
	Long userId,
	Long weeklyRecordId,
	String title,
	String reason,
	int amount,
	LocalDate dueDate
) {
}
