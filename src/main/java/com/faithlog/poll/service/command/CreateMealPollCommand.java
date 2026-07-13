package com.faithlog.poll.service.command;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CreateMealPollCommand(
	Long campusId,
	Long requesterId,
	String title,
	boolean isAnonymous,
	boolean allowUserOptionAdd,
	Instant endsAt,
	List<CreateMealPollOptionCommand> options,
	Set<String> unknownFields
) {
}
