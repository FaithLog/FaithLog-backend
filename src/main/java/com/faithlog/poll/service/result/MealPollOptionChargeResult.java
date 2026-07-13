package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.type.MealChargeCalculationType;
import com.faithlog.poll.domain.type.MealPollOptionChargeStatus;
import java.time.Instant;

public record MealPollOptionChargeResult(
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

	public static MealPollOptionChargeResult notCharged() {
		return new MealPollOptionChargeResult(
			MealPollOptionChargeStatus.NOT_CHARGED, null, null, null, null, null, null, null, false, null
		);
	}
}
