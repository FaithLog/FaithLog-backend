package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.MealPollManagementOptionResult;

public record MealPollManagementOptionResponse(
	Long optionId,
	String content,
	int responseCount,
	boolean userAdded,
	MealPollOptionChargeResponse charge
) {

	public static MealPollManagementOptionResponse from(MealPollManagementOptionResult result) {
		return new MealPollManagementOptionResponse(
			result.optionId(), result.content(), result.responseCount(), result.userAdded(),
			MealPollOptionChargeResponse.from(result.charge())
		);
	}
}
