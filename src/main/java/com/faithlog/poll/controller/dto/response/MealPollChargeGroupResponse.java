package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.domain.type.MealChargeCalculationType;
import com.faithlog.poll.service.result.MealPollChargeGroupResult;

public record MealPollChargeGroupResponse(
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

	public static MealPollChargeGroupResponse from(MealPollChargeGroupResult result) {
		return new MealPollChargeGroupResponse(
			result.optionId(), result.content(), result.calculationType(), result.enteredAmount(),
			result.responseCount(), result.amountPerMember(), result.requestedTotalAmount(),
			result.actualTotalAmount(), result.roundingAdjustment()
		);
	}
}
