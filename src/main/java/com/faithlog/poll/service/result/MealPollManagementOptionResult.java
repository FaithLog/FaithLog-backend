package com.faithlog.poll.service.result;

public record MealPollManagementOptionResult(
	Long optionId,
	String content,
	int responseCount,
	boolean userAdded,
	MealPollOptionChargeResult charge
) {
}
