package com.faithlog.poll.service.result;

import java.time.Instant;
import java.util.List;

public record MealPollSettlementResult(
	Long pollId,
	Long paymentAccountId,
	int chargedMemberCount,
	long requestedTotalAmount,
	long actualTotalAmount,
	long roundingAdjustment,
	Instant chargedAt,
	List<MealPollChargeGroupResult> groups
) {
}
