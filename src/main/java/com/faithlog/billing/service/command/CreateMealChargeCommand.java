package com.faithlog.billing.service.command;

public record CreateMealChargeCommand(
	Long campusId,
	Long userId,
	Long requesterId,
	Long paymentAccountId,
	Long pollResponseId,
	String title,
	int amount
) {
}
