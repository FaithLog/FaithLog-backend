package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.MealPollSettlementResult;
import java.time.Instant;
import java.util.List;

public record MealPollSettlementResponse(
	Long pollId,
	Long paymentAccountId,
	int chargedMemberCount,
	long requestedTotalAmount,
	long actualTotalAmount,
	long roundingAdjustment,
	Instant chargedAt,
	List<MealPollChargeGroupResponse> groups
) {

	public static MealPollSettlementResponse from(MealPollSettlementResult result) {
		return new MealPollSettlementResponse(
			result.pollId(), result.paymentAccountId(), result.chargedMemberCount(),
			result.requestedTotalAmount(), result.actualTotalAmount(), result.roundingAdjustment(),
			result.chargedAt(), result.groups().stream().map(MealPollChargeGroupResponse::from).toList()
		);
	}
}
