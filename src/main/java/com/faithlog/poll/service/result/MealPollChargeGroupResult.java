package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.type.MealChargeCalculationType;

public record MealPollChargeGroupResult(
	Long optionId,
	String content,
	MealChargeCalculationType calculationType,
	long enteredAmount,
	int responseCount,
	int amountPerMember,
	long requestedTotalAmount,
	long actualTotalAmount,
	long roundingAdjustment
) {
}
