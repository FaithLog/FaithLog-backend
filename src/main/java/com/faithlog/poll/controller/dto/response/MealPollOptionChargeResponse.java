package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.domain.type.MealChargeCalculationType;
import com.faithlog.poll.domain.type.MealPollOptionChargeStatus;
import com.faithlog.poll.service.result.MealPollOptionChargeResult;
import java.time.Instant;

public record MealPollOptionChargeResponse(
	MealPollOptionChargeStatus chargeStatus,
	MealChargeCalculationType calculationType,
	Long enteredAmount,
	Integer amountPerMember,
	Long requestedTotalAmount,
	Long actualTotalAmount,
	Long roundingAdjustment,
	Long paymentAccountId,
	boolean chargedByMe,
	Instant chargedAt
) {

	public static MealPollOptionChargeResponse from(MealPollOptionChargeResult result) {
		return new MealPollOptionChargeResponse(
			result.chargeStatus(), result.calculationType(), result.enteredAmount(), result.amountPerMember(),
			result.requestedTotalAmount(), result.actualTotalAmount(), result.roundingAdjustment(),
			result.paymentAccountId(), result.chargedByMe(), result.chargedAt()
		);
	}
}
