package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.domain.type.MealPollSettlementStatus;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.service.result.MealPollManagementListItemResult;
import java.time.Instant;

public record MealPollManagementListItemResponse(
	Long id,
	String title,
	PollStatus status,
	Instant startsAt,
	Instant endsAt,
	MealPollSettlementStatus settlementStatus
) {

	public static MealPollManagementListItemResponse from(MealPollManagementListItemResult result) {
		return new MealPollManagementListItemResponse(
			result.id(), result.title(), result.status(), result.startsAt(), result.endsAt(), result.settlementStatus()
		);
	}
}
