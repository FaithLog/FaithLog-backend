package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.service.result.MealPollManagementDetailResult;
import java.time.Instant;
import java.util.List;

public record MealPollManagementDetailResponse(
	Long id,
	Long campusId,
	String title,
	PollType pollType,
	SelectionType selectionType,
	boolean isAnonymous,
	boolean allowUserOptionAdd,
	Instant startsAt,
	Instant endsAt,
	PollStatus status,
	List<MealPollManagementOptionResponse> options
) {

	public static MealPollManagementDetailResponse from(MealPollManagementDetailResult result) {
		return new MealPollManagementDetailResponse(
			result.id(), result.campusId(), result.title(), result.pollType(), result.selectionType(),
			result.isAnonymous(), result.allowUserOptionAdd(), result.startsAt(), result.endsAt(), result.status(),
			result.options().stream().map(MealPollManagementOptionResponse::from).toList()
		);
	}
}
