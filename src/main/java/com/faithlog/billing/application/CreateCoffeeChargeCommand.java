package com.faithlog.billing.application;

import java.time.LocalDate;

public record CreateCoffeeChargeCommand(
	Long campusId,
	Long userId,
	Long paymentAccountId,
	Long sourceId,
	String title,
	String reason,
	int amount,
	LocalDate dueDate
) {
}
