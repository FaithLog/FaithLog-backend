package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import java.time.Instant;
import java.util.List;

public record MealPollManagementDetailResult(
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
	List<MealPollManagementOptionResult> options
) {
}
