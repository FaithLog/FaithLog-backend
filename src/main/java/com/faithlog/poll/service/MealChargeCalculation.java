package com.faithlog.poll.service;

public record MealChargeCalculation(
	int amountPerMember,
	long requestedTotalAmount,
	long actualTotalAmount,
	long roundingAdjustment
) {
}
