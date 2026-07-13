package com.faithlog.poll.service.command;

import com.faithlog.poll.domain.type.MealChargeCalculationType;

public record CreateMealPollChargeGroupCommand(
	Long optionId,
	MealChargeCalculationType calculationType,
	long enteredAmount
) {
}
